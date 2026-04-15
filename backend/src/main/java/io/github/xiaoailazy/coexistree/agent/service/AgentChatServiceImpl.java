package io.github.xiaoailazy.coexistree.agent.service;

import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.RunConfig;
import com.google.adk.events.Event;
import com.google.adk.runner.InMemoryRunner;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.FunctionResponse;
import com.google.genai.types.Part;
import io.github.xiaoailazy.coexistree.agent.context.AgentUserContext;
import io.github.xiaoailazy.coexistree.agent.observability.AgentExecutionLogger;
import io.github.xiaoailazy.coexistree.chat.dto.ChatRequest;
import io.github.xiaoailazy.coexistree.chat.dto.SseEvent;
import io.github.xiaoailazy.coexistree.chat.entity.MessageEntity;
import io.github.xiaoailazy.coexistree.chat.repository.MessageRepository;
import io.github.xiaoailazy.coexistree.security.model.SecurityUserDetails;
import io.reactivex.rxjava3.core.Flowable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
public class AgentChatServiceImpl implements AgentChatService {

    private final BaseAgent rootAgent;
    private final MessageRepository messageRepository;
    private final AgentExecutionLogger executionLogger;

    public AgentChatServiceImpl(
            @Qualifier("rootAgent") BaseAgent rootAgent,
            MessageRepository messageRepository,
            AgentExecutionLogger executionLogger
    ) {
        this.rootAgent = rootAgent;
        this.messageRepository = messageRepository;
        this.executionLogger = executionLogger;
    }

    @Override
    public void smartChatStream(
            String conversationId,
            ChatRequest request,
            SseEmitter emitter,
            SecurityUserDetails userDetails
    ) {
        AgentUserContext context = AgentUserContext.fromUser(
                userDetails, null, null, conversationId);

        try {
            // Save user message
            saveUserMessage(conversationId, request.question());

            // Load conversation history
            List<MessageEntity> history = messageRepository
                    .findByConversationIdOrderByCreatedAt(conversationId);
            List<MessageEntity> recentUserMessages = history.stream()
                    .filter(m -> "USER".equals(m.getRole()))
                    .limit(10)
                    .toList();

            String userInput = buildUserInput(request.question(), recentUserMessages);

            executionLogger.logStart("root-agent");

            // Run agent in background thread so SSE can stream in real-time
            runAgentWithSse(context, conversationId, userInput, emitter);

        } catch (Exception e) {
            log.error("Agent 对话失败, conversationId={}", conversationId, e);
            try {
                emitter.send(SseEmitter.event().data(SseEvent.error(e.getMessage())));
            } catch (IOException ignored) {}
            emitter.complete();
        }
    }

    private void saveUserMessage(String conversationId, String content) {
        MessageEntity userMsg = new MessageEntity();
        userMsg.setConversationId(conversationId);
        userMsg.setRole("USER");
        userMsg.setContent(content);
        userMsg.setCreatedAt(LocalDateTime.now());
        messageRepository.save(userMsg);
    }

    private void saveAssistantMessage(String conversationId, String content, String thinking) {
        MessageEntity assistantMsg = new MessageEntity();
        assistantMsg.setConversationId(conversationId);
        assistantMsg.setRole("ASSISTANT");
        assistantMsg.setContent(content);
        assistantMsg.setThinking(thinking);
        assistantMsg.setCreatedAt(LocalDateTime.now());
        messageRepository.save(assistantMsg);
    }

    private String buildUserInput(String question, List<MessageEntity> history) {
        if (history.isEmpty()) {
            return question;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("对话历史:\n");
        for (MessageEntity msg : history) {
            String role = "USER".equals(msg.getRole()) ? "用户" : "助手";
            sb.append(role).append(": ").append(msg.getContent()).append("\n");
        }
        sb.append("\n当前问题: ").append(question);
        return sb.toString();
    }

    /**
     * Run the root agent via ADK InMemoryRunner and translate events to SSE.
     *
     * ADK API:
     * - InMemoryRunner(BaseAgent) creates a runner with in-memory session management
     * - runAsync(userId, sessionId, Content) returns Flowable<Event>
     * - Each Event may contain: partial text, function calls, function responses, turn completion flag
     */
    private void runAgentWithSse(AgentUserContext context, String conversationId,
                                  String userInput, SseEmitter emitter) {
        try {
            InMemoryRunner runner = new InMemoryRunner(rootAgent, "coexistree");

            Content userContent = Content.fromParts(Part.fromText(userInput));

            String userId = context.userId() != null ? context.userId().toString() : "anonymous";
            String sessionId = conversationId != null ? conversationId : "default-session";

            RunConfig runConfig = RunConfig.builder()
                    .autoCreateSession(true)
                    .build();

            Flowable<Event> events = runner.runAsync(userId, sessionId, userContent, runConfig);

            AtomicReference<StringBuilder> thinkingBuffer = new AtomicReference<>(new StringBuilder());
            AtomicReference<StringBuilder> answerBuffer = new AtomicReference<>(new StringBuilder());
            AtomicBoolean hasThinking = new AtomicBoolean(false);
            AtomicReference<String> lastToolName = new AtomicReference<>();
            long startTime = System.currentTimeMillis();

            events.subscribe(
                    event -> {
                        try {
                            handleEvent(event, emitter, thinkingBuffer, answerBuffer,
                                    hasThinking, lastToolName, startTime, conversationId, executionLogger);
                        } catch (IOException e) {
                            log.error("SSE 发送事件失败", e);
                        }
                    },
                    error -> {
                        log.error("Agent 执行异常", error);
                        executionLogger.logError("root-agent", error.getMessage());
                        try {
                            emitter.send(SseEmitter.event().data(SseEvent.error(error.getMessage())));
                        } catch (IOException ignored) {}
                        emitter.complete();
                    },
                    () -> {
                        // onComplete — agent finished successfully
                        String answer = answerBuffer.get().toString();
                        String thinking = thinkingBuffer.get().toString();

                        if (!answer.isEmpty()) {
                            saveAssistantMessage(conversationId, answer,
                                    hasThinking.get() ? thinking : null);
                        }

                        long elapsed = System.currentTimeMillis() - startTime;
                        executionLogger.logComplete("root-agent", 0, elapsed);

                        if (!answer.isEmpty()) {
                            try {
                                emitter.send(SseEmitter.event().data(SseEvent.answer(answer)));
                                emitter.send(SseEmitter.event().data(SseEvent.done(true)));
                            } catch (IOException ignored) {}
                        }
                        emitter.complete();
                    }
            );

        } catch (Exception e) {
            log.error("Agent 启动失败", e);
            try {
                emitter.send(SseEmitter.event().data(SseEvent.error("Agent 启动失败: " + e.getMessage())));
            } catch (IOException ignored) {}
            emitter.complete();
        }
    }

    private void handleEvent(
            Event event,
            SseEmitter emitter,
            AtomicReference<StringBuilder> thinkingBuffer,
            AtomicReference<StringBuilder> answerBuffer,
            AtomicBoolean hasThinking,
            AtomicReference<String> lastToolName,
            long startTime,
            String conversationId,
            AgentExecutionLogger executionLogger
    ) throws IOException {
        String agentAuthor = event.author();
        if (agentAuthor == null) {
            agentAuthor = "root-agent";
        }

        // Check for tool calls (function calls)
        List<FunctionCall> functionCalls = event.functionCalls();
        if (functionCalls != null && !functionCalls.isEmpty()) {
            for (FunctionCall fc : functionCalls) {
                String toolName = fc.name().orElse("unknown");
                lastToolName.set(toolName);
                String args = fc.args().map(Object::toString).orElse("");
                executionLogger.logToolCall(agentAuthor, toolName, args);
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
            emitter.send(SseEmitter.event().data(SseEvent.stage("search", "success")));
            return;
        }

        // Check for partial text (streaming content)
        boolean isPartial = event.partial().orElse(false);
        boolean isTurnComplete = event.turnComplete().orElse(false);

        String text = event.stringifyContent();
        if (text != null && !text.isBlank()) {
            // Check if it looks like thinking/reasoning content
            // ADK may separate thinking from answer based on content type
            if (isPartial) {
                // Streaming in progress — accumulate as answer
                if (hasThinking.get()) {
                    thinkingBuffer.get().append(text);
                    emitter.send(SseEmitter.event().data(SseEvent.thinking(text)));
                } else {
                    answerBuffer.get().append(text);
                    // Don't stream answer fragments to avoid flicker; collect silently
                }
            } else {
                // Complete event — could be a final answer or intermediate step
                if (isTurnComplete) {
                    // Final answer
                    answerBuffer.get().setLength(0);
                    answerBuffer.get().append(text);
                }
            }
        }
    }
}
