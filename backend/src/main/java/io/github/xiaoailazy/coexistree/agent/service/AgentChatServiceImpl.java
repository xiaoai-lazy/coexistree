package io.github.xiaoailazy.coexistree.agent.service;

import com.google.adk.agents.RunConfig;
import com.google.adk.agents.RunConfig.StreamingMode;
import com.google.adk.events.Event;
import com.google.adk.runner.Runner;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.FunctionResponse;
import com.google.genai.types.Part;
import io.github.xiaoailazy.coexistree.agent.observability.AgentExecutionLogger;
import io.github.xiaoailazy.coexistree.agent.observability.EventContentParser;
import io.github.xiaoailazy.coexistree.agent.session.DatabaseSessionService;
import io.github.xiaoailazy.coexistree.chat.dto.ChatRequest;
import io.github.xiaoailazy.coexistree.chat.dto.SseEvent;
import io.github.xiaoailazy.coexistree.chat.entity.ConversationEntity;
import io.github.xiaoailazy.coexistree.chat.repository.ConversationRepository;
import io.github.xiaoailazy.coexistree.chat.repository.MessageRepository;
import io.github.xiaoailazy.coexistree.chat.service.ChatSourceService;
import io.github.xiaoailazy.coexistree.chat.service.MessageService;
import io.github.xiaoailazy.coexistree.document.service.DocumentAccessService;
import io.github.xiaoailazy.coexistree.observability.service.ConversationRunService;
import io.github.xiaoailazy.coexistree.observability.service.SpanEventBuffer;
import io.github.xiaoailazy.coexistree.observability.context.SpanContextRegistry;
import io.github.xiaoailazy.coexistree.security.model.SecurityUserDetails;
import io.reactivex.rxjava3.core.Flowable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import org.slf4j.MDC;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
public class AgentChatServiceImpl implements AgentChatService {

    private final Runner runner;
    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final AgentExecutionLogger executionLogger;
    private final MessageService messageService;
    private final ConversationRunService conversationRunService;
    private final SpanEventBuffer spanEventBuffer;
    private final SpanContextRegistry spanContextRegistry;
    private final DatabaseSessionService sessionService;
    private final DocumentAccessService documentAccessService;
    private final ChatSourceService chatSourceService;

    public AgentChatServiceImpl(
            Runner runner,
            MessageRepository messageRepository,
            ConversationRepository conversationRepository,
            AgentExecutionLogger executionLogger,
            MessageService messageService,
            ConversationRunService conversationRunService,
            SpanEventBuffer spanEventBuffer,
            SpanContextRegistry spanContextRegistry,
            DatabaseSessionService sessionService,
            DocumentAccessService documentAccessService,
            ChatSourceService chatSourceService
    ) {
        this.runner = runner;
        this.messageRepository = messageRepository;
        this.conversationRepository = conversationRepository;
        this.executionLogger = executionLogger;
        this.messageService = messageService;
        this.conversationRunService = conversationRunService;
        this.spanEventBuffer = spanEventBuffer;
        this.spanContextRegistry = spanContextRegistry;
        this.sessionService = sessionService;
        this.documentAccessService = documentAccessService;
        this.chatSourceService = chatSourceService;
    }

    @Override
    public void smartChatStream(
            String conversationId,
            ChatRequest request,
            SseEmitter emitter,
            SecurityUserDetails userDetails
    ) {
        log.info("[chat] smartChatStream received, conv={}, user={}, requestSystemId={}, question={}",
                conversationId, userDetails.getUsername(), request.systemId(), truncate(request.question(), 100));

        Thread.startVirtualThread(() -> {
            try {
                emitter.send(SseEmitter.event().data(SseEvent.stage("init", "running")));

                Long systemId = request.systemId();
                if (systemId == null) {
                    var conversationOpt = conversationRepository.findByConversationId(conversationId);
                    systemId = conversationOpt.map(ConversationEntity::getSystemId).orElse(null);
                }

                log.info("[chat] smartChatStream start, conv={}, user={}, systemId={}, question={}",
                        conversationId, userDetails.getUsername(), systemId, truncate(request.question(), 100));

                if (systemId != null) {
                    documentAccessService.checkSystemAccess(systemId, userDetails);
                }

                messageService.saveUserMessage(conversationId, request.question());
                executionLogger.logStart("root-agent");

                emitter.send(SseEmitter.event().data(SseEvent.stage("search", "running")));
                List<SseEvent.SourceDto> sources = systemId != null
                        ? chatSourceService.retrieveSources(systemId, request.question(), userDetails)
                        : List.of();
                emitter.send(SseEmitter.event().data(SseEvent.sources(sources)));
                emitter.send(SseEmitter.event().data(SseEvent.stage("thinking", "running")));

                runAgentWithSse(conversationId, request.question(), emitter, systemId, userDetails, sources);

            } catch (Exception e) {
                log.error("[chat] smartChatStream failed, conv={}", conversationId, e);
                try {
                    emitter.send(SseEmitter.event().data(SseEvent.error(e.getMessage())));
                } catch (IOException ignored) {}
                emitter.complete();
            }
        });
    }

    private void saveAssistantMessage(String conversationId, String content, String thinking) {
        messageService.saveAssistantMessage(conversationId, content, thinking);
    }

    /**
     * Run the root agent via ADK Runner and translate events to SSE.
     *
     * ADK API:
     * - Runner(app, sessionService, artifactService) creates a runner with DB-backed sessions
     * - runAsync(userId, sessionId, Content, RunConfig) returns Flowable<Event>
     * - Each Event may contain: partial text, function calls, function responses, turn completion flag
     * - ADK automatically manages conversation history when using the same sessionId
     */
    private void runAgentWithSse(String conversationId, String userMessage,
                                  SseEmitter emitter, Long systemId,
                                  SecurityUserDetails userDetails,
                                  List<SseEvent.SourceDto> sources) {
        try {
            // Prepare user content from the current message
            Content userContent = Content.fromParts(Part.fromText(userMessage));

            String userId = userDetails.getUsername() != null ? userDetails.getUsername() : "anonymous";
            String sessionId = conversationId != null ? conversationId : "default-session";

            // Create run boundary FIRST so we have the runId for the state
            String correlationId = MDC.get("correlationId");
            Long triggerMessageId = findLatestUserMessageId(conversationId);
            Long runUserId = userDetails.getId();
            Long runSystemId = systemId;

            var runCtx = conversationRunService.createRun(
                    conversationId, triggerMessageId, runUserId, runSystemId,
                    "root-agent", userMessage, correlationId);
            log.info("[chat] run created, runId={}, conv={}, user={}", runCtx.runId(), conversationId, userId);

            // Manually create session with temp:runId pre-loaded so callbacks
            // can see it immediately (stateDelta via runAsync is delayed until
            // append_event, which is too late for the first callback invocation).
            ConcurrentHashMap<String, Object> sessionState = new ConcurrentHashMap<>();
            sessionState.put("temp:runId", runCtx.runId());
            sessionState.put("user:userId", userDetails.getId());
            if (systemId != null) {
                sessionState.put("user:systemId", systemId);
            }
            sessionState.put("user:conversationId", conversationId);
            sessionState.put("user:readableDocIds", sources.stream()
                    .map(SseEvent.SourceDto::docId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList());

            sessionService.createSession("coexistree", userId, sessionState, sessionId).blockingGet();
            log.info("[chat] session created, sessionId={}, userId={}", sessionId, userId);

            // Call runAsync with autoCreateSession=false — session already exists with state
            RunConfig runConfig = RunConfig.builder()
                    .autoCreateSession(false)
                    .streamingMode(StreamingMode.SSE)
                    .build();

            Flowable<Event> events = runner.runAsync(userId, sessionId, userContent, runConfig);
            log.info("[chat] ADK runner started, userId={}, sessionId={}", userId, sessionId);

            AtomicReference<StringBuilder> thinkingBuffer = new AtomicReference<>(new StringBuilder());
            AtomicReference<StringBuilder> answerBuffer = new AtomicReference<>(new StringBuilder());
            AtomicReference<String> lastToolName = new AtomicReference<>();
            AtomicBoolean streamStarted = new AtomicBoolean(false);
            long startTime = System.currentTimeMillis();

            events.subscribe(
                    event -> {
                        try {
                            handleEvent(event, emitter, thinkingBuffer, answerBuffer,
                                    lastToolName, streamStarted, startTime,
                                    conversationId, executionLogger, runCtx.runId(),
                                    spanContextRegistry);
                        } catch (IOException e) {
                            log.error("SSE 发送事件失败", e);
                        }
                    },
                    error -> {
                        long elapsed = System.currentTimeMillis() - startTime;
                        log.error("[chat] SSE error, runId={}, conv={}, elapsed={}ms, error={}",
                                runCtx.runId(), conversationId, elapsed, error.getMessage(), error);
                        executionLogger.logError("root-agent", error.getMessage());
                        spanEventBuffer.markRunFailed(runCtx.runId());
                        conversationRunService.markRunFailed(runCtx.runId(), "agent_error", error.getMessage(), elapsed);
                        try {
                            emitter.send(SseEmitter.event().data(SseEvent.error(error.getMessage())));
                        } catch (IOException ignored) {}
                        emitter.complete();
                    },
                    () -> {
                        // onComplete — agent finished successfully
                        String answer = answerBuffer.get().toString();
                        String thinking = thinkingBuffer.get().toString();

                        log.info("[chat][stream][complete] final answer (accumulated), len={}, content={}",
                                answer.length(), truncate(answer, 200));
                        log.info("[chat][stream][complete] final thinking (accumulated), len={}, content={}",
                                thinking.length(), truncate(thinking, 200));

                        if (!answer.isEmpty()) {
                            saveAssistantMessage(conversationId, answer,
                                    !thinking.isEmpty() ? thinking : null);
                        }

                        long elapsed = System.currentTimeMillis() - startTime;
                        executionLogger.logComplete("root-agent", 0, elapsed);
                        spanEventBuffer.flushAndClose(runCtx.runId());
                        conversationRunService.markRunSuccess(runCtx.runId(), answer, elapsed);
                        log.info("[chat] run complete, runId={}, conv={}, elapsed={}ms, answerLen={}, thinkingLen={}",
                                runCtx.runId(), conversationId, elapsed, answer.length(), thinking.length());

                        try {
                            emitter.send(SseEmitter.event().data(SseEvent.done(true)));
                        } catch (IOException ignored) {}
                        MDC.clear();
                        emitter.complete();
                    }
            );

        } catch (Exception e) {
            log.error("Agent 启动失败", e);
            try {
                emitter.send(SseEmitter.event().data(SseEvent.error("Agent 启动失败: " + e.getClass().getName() + ": " + e.getMessage())));
            } catch (IOException ignored) {}
            emitter.complete();
        }
    }

    private void handleEvent(
            Event event,
            SseEmitter emitter,
            AtomicReference<StringBuilder> thinkingBuffer,
            AtomicReference<StringBuilder> answerBuffer,
            AtomicReference<String> lastToolName,
            AtomicBoolean streamStarted,
            long startTime,
            String conversationId,
            AgentExecutionLogger executionLogger,
            String runId,
            SpanContextRegistry spanContextRegistry
    ) throws IOException {
        String agentAuthor = event.author();
        if (agentAuthor == null) {
            agentAuthor = "root-agent";
            log.debug("[chat][handleEvent] author is null, defaulting to root-agent");
        }

        // Resolve actual span ID from the registry (root agent span for SSE events)
        String spanId = spanContextRegistry.findLatestActiveSpanId(runId);
        if (spanId == null) {
            log.debug("[chat][handleEvent] no active span for runId={}, using synthetic ID", runId);
        }

        // Check for tool calls (function calls)
        List<FunctionCall> functionCalls = event.functionCalls();
        if (functionCalls != null && !functionCalls.isEmpty()) {
            for (FunctionCall fc : functionCalls) {
                String toolName = fc.name().orElse("unknown");
                lastToolName.set(toolName);
                String args = fc.args().map(Object::toString).orElse("");
                executionLogger.logToolCall(agentAuthor, toolName, args);

                log.info("[chat][handleEvent] tool_decision, runId={}, agent={}, tool={}, args={}",
                        runId, agentAuthor, toolName, truncate(args, 200));

                spanEventBuffer.enqueue(spanId != null ? spanId : generateEventSpanId(),
                        runId, conversationId, "tool_decision", agentAuthor, null,
                        Map.of("toolName", toolName, "args", args));

                emitter.send(SseEmitter.event().data(SseEvent.stage("search", "running")));
            }
            return;
        }

        // Check for tool responses (function responses)
        List<FunctionResponse> functionResponses = event.functionResponses();
        if (functionResponses != null && !functionResponses.isEmpty()) {
            String toolName = lastToolName.get() != null ? lastToolName.get() : "tool";
            long elapsed = System.currentTimeMillis() - startTime;
            executionLogger.logToolResult(agentAuthor, toolName, elapsed);

            log.info("[chat][handleEvent] tool_response, runId={}, agent={}, tool={}",
                    runId, agentAuthor, toolName);

            emitter.send(SseEmitter.event().data(SseEvent.stage("search", "success")));
            return;
        }

        // Use EventContentParser to separate thinking from answer content
        EventContentParser.ParsedContent parsed = EventContentParser.parse(event);

        // Log raw chunk data for debugging stream truncation
        log.info("[chat][chunk] event#{} author={}, thinkingLen={}, answerLen={}, thinkingPreview={}, answerPreview={}",
                streamStarted.get() ? "ongoing" : "first",
                agentAuthor,
                parsed.thinkingText().length(),
                parsed.answerText().length(),
                truncate(parsed.thinkingText(), 80),
                truncate(parsed.answerText(), 80));

        // Fire stream_started on first text
        if (!streamStarted.get() && (!parsed.thinkingText().isEmpty() || !parsed.answerText().isEmpty())) {
            streamStarted.set(true);
            log.info("[chat][handleEvent] stream_started, runId={}, agent={}", runId, agentAuthor);
            spanEventBuffer.enqueue(spanId != null ? spanId : generateEventSpanId(),
                    runId, conversationId, "stream_started", agentAuthor, null,
                    Map.of("author", agentAuthor));
        }

        // Stream thinking chunks immediately if present
        if (!parsed.thinkingText().isEmpty()) {
            thinkingBuffer.get().append(parsed.thinkingText());
            log.info("[chat][chunk][thinking] sending delta, len={}, content={}",
                    parsed.thinkingText().length(), truncate(parsed.thinkingText(), 100));
            emitter.send(SseEmitter.event().data(SseEvent.thinking(parsed.thinkingText())));
            spanEventBuffer.enqueue(spanId != null ? spanId : generateEventSpanId(),
                    runId, conversationId, "thinking_delta_batch", agentAuthor, null,
                    Map.of("contentPreview", parsed.thinkingText()));
        }

        // Stream answer chunks immediately if present
        if (!parsed.answerText().isEmpty()) {
            String newAnswer = parsed.answerText();
            StringBuilder buf = answerBuffer.get();
            boolean partial = event.partial().orElse(false);

            if (partial || buf.length() == 0) {
                buf.append(newAnswer);
                log.info("[chat][chunk][answer] sending new, len={}, content={}",
                        newAnswer.length(), truncate(newAnswer, 100));
                emitter.send(SseEmitter.event().data(SseEvent.answer(newAnswer)));
            } else {
                String accumulated = buf.toString();

                if (newAnswer.equals(accumulated)) {
                    log.debug("[chat][chunk][answer] exact duplicate, skipping, content={}",
                            truncate(newAnswer, 50));
                } else if (newAnswer.startsWith(accumulated)) {
                    String toSend = newAnswer.substring(accumulated.length());
                    if (!toSend.isEmpty()) {
                        log.info("[chat][chunk][answer] sending cumulative suffix, len={}, content={}",
                                toSend.length(), truncate(toSend, 100));
                        buf.append(toSend);
                        emitter.send(SseEmitter.event().data(SseEvent.answer(toSend)));
                    }
                } else {
                    // Find overlap: does suffix of accumulated match prefix of newAnswer?
                    String toSend = newAnswer;
                    int overlapStart = -1;
                    int maxOverlap = Math.min(accumulated.length(), newAnswer.length());
                    for (int i = maxOverlap; i > 0; i--) {
                        if (accumulated.endsWith(newAnswer.substring(0, i))) {
                            overlapStart = i;
                            break;
                        }
                    }
                    if (overlapStart > 0) {
                        toSend = newAnswer.substring(overlapStart);
                        log.info("[chat][chunk][answer] overlap detected={}, sending={}, content={}",
                                overlapStart, toSend.length(), truncate(toSend, 100));
                    } else {
                        log.info("[chat][chunk][answer] sending incremental, len={}, content={}",
                                toSend.length(), truncate(toSend, 100));
                    }
                    if (!toSend.isEmpty()) {
                        buf.append(toSend);
                        emitter.send(SseEmitter.event().data(SseEvent.answer(toSend)));
                    }
                }
            }
        }
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }

    private String generateEventSpanId() {
        return "evt-" + java.util.UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * Find the ID of the latest user message in a conversation.
     */
    private Long findLatestUserMessageId(String conversationId) {
        return messageRepository.findLatestUserMessageId(conversationId);
    }
}
