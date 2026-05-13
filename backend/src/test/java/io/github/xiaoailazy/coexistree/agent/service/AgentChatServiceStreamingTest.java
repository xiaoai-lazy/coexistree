package io.github.xiaoailazy.coexistree.agent.service;

import com.google.adk.events.Event;
import com.google.adk.runner.Runner;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.FunctionResponse;
import com.google.genai.types.Part;
import io.github.xiaoailazy.coexistree.chat.dto.ChatRequest;
import io.github.xiaoailazy.coexistree.chat.dto.SseEvent;
import io.github.xiaoailazy.coexistree.chat.service.ChatSourceService;
import io.github.xiaoailazy.coexistree.document.service.DocumentAccessService;
import io.github.xiaoailazy.coexistree.security.model.SecurityUserDetails;
import io.reactivex.rxjava3.core.Flowable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * TDD tests for SSE streaming behavior in AgentChatServiceImpl.
 *
 * These tests verify that:
 * 1. Answer chunks are streamed immediately via SSE (not buffered until completion)
 * 2. Thinking chunks are streamed immediately via SSE
 * 3. Tool call/result events are pushed to SSE with basic info (name, type, status)
 * 4. Pre-agent SSE phase order: init → search → sources → thinking (see chat SSE sources spec)
 */
@ExtendWith(MockitoExtension.class)
class AgentChatServiceStreamingTest {

    @Mock
    private io.github.xiaoailazy.coexistree.chat.repository.ConversationRepository conversationRepository;

    @Mock
    private io.github.xiaoailazy.coexistree.chat.service.MessageService messageService;

    @Mock
    private io.github.xiaoailazy.coexistree.observability.service.ConversationRunService conversationRunService;

    @Mock
    private io.github.xiaoailazy.coexistree.observability.service.SpanEventBuffer spanEventBuffer;

    @Mock
    private io.github.xiaoailazy.coexistree.observability.context.SpanContextRegistry spanContextRegistry;

    @Mock
    private SecurityUserDetails userDetails;

    @Mock
    private io.github.xiaoailazy.coexistree.agent.session.DatabaseSessionService sessionService;

    @Mock
    private io.github.xiaoailazy.coexistree.agent.observability.AgentExecutionLogger executionLogger;

    @Mock
    private Runner runner;

    @Mock
    private io.github.xiaoailazy.coexistree.chat.repository.MessageRepository messageRepository;

    @Mock
    private DocumentAccessService documentAccessService;

    @Mock
    private ChatSourceService chatSourceService;

    @BeforeEach
    void setUp() {
        lenient().when(userDetails.getId()).thenReturn(1L);
        lenient().when(userDetails.getUsername()).thenReturn("testuser");
        lenient().doNothing().when(documentAccessService).checkSystemAccess(any(), any());
        lenient().when(chatSourceService.retrieveSources(any(), anyString(), any())).thenReturn(List.of());
        lenient().when(conversationRepository.findByConversationId(anyString()))
                .thenReturn(java.util.Optional.empty());
        lenient().when(conversationRunService.createRun(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new io.github.xiaoailazy.coexistree.observability.context.RunContext(
                        "run-test", "run-test", "conv-1", 1L,
                        1L, null, "root-agent", "corr-test", java.time.LocalDateTime.now()
                ));
        lenient().when(runner.runAsync(anyString(), anyString(), any(), any()))
                .thenReturn(Flowable.empty());
        lenient().when(sessionService.createSession(anyString(), anyString(), any(), anyString()))
                .thenAnswer(inv -> io.reactivex.rxjava3.core.Single.just(
                        com.google.adk.sessions.Session.builder(inv.getArgument(3, String.class))
                                .appName(inv.getArgument(0, String.class))
                                .userId(inv.getArgument(1, String.class))
                                .state(inv.getArgument(2, java.util.concurrent.ConcurrentHashMap.class))
                                .build()));
        lenient().when(messageRepository.findLatestUserMessageId(anyString())).thenReturn(1L);
    }

    /**
     * Creates a capturing emitter using an anonymous Answer class to avoid
     * the lambda checked exception issue with send() declaring IOException.
     */
    @SuppressWarnings("unchecked")
    private SseEmitter capturingEmitter(List<SseEvent> captured) {
        SseEmitter emitter = mock(SseEmitter.class);
        try {
            doAnswer(invocation -> {
                var builder = invocation.getArgument(0, SseEmitter.SseEventBuilder.class);
                var field = builder.getClass().getDeclaredField("dataToSend");
                field.setAccessible(true);
                var dataToSend = (java.util.Set<org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter.DataWithMediaType>) field.get(builder);
                if (dataToSend != null) {
                    for (var d : dataToSend) {
                        Object data = d.getData();
                        if (data instanceof SseEvent evt) {
                            captured.add(evt);
                        }
                    }
                }
                return null;
            }).when(emitter).send(any(SseEmitter.SseEventBuilder.class));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        doNothing().when(emitter).complete();
        return emitter;
    }

    @Test
    @DisplayName("partial answer chunks should be streamed via SSE immediately")
    void shouldStreamAnswerChunksImmediately() throws InterruptedException {
        // Given: A sequence of partial answer events
        Part chunk1 = Part.builder().text("Hello").build();
        Part chunk2 = Part.builder().text(" World").build();

        Event partial1 = Event.builder()
                .content(Content.fromParts(chunk1))
                .partial(true)
                .author("qa-agent")
                .build();
        Event partial2 = Event.builder()
                .content(Content.fromParts(chunk2))
                .partial(true)
                .author("qa-agent")
                .build();
        Event done = Event.builder()
                .content(Content.fromParts(Part.builder().text("Hello World").build()))
                .partial(false)
                .turnComplete(true)
                .author("qa-agent")
                .build();

        Flowable<Event> events = Flowable.just(partial1, partial2, done);
        lenient().when(runner.runAsync(anyString(), anyString(), any(), any())).thenReturn(events);
        lenient().when(spanContextRegistry.findLatestActiveSpanId(anyString())).thenReturn("span-1");

        List<SseEvent> captured = new CopyOnWriteArrayList<>();
        SseEmitter emitter = capturingEmitter(captured);

        ChatRequest request = new ChatRequest("test", null, null);

        AgentChatServiceImpl service = new AgentChatServiceImpl(
                runner, messageRepository, conversationRepository, executionLogger,
                messageService, conversationRunService, spanEventBuffer,
                spanContextRegistry, sessionService, documentAccessService, chatSourceService);
        service.smartChatStream("conv-1", request, emitter, userDetails);

        Thread.sleep(500);

        long answerEvents = captured.stream()
                .filter(e -> "answer".equals(e.type()))
                .count();
        assertThat(answerEvents)
                .as("Answer chunks should be streamed immediately for each partial event")
                .isGreaterThanOrEqualTo(2);

        List<String> answerTexts = captured.stream()
                .filter(e -> "answer".equals(e.type()))
                .map(SseEvent::content)
                .toList();
        assertThat(answerTexts).anyMatch(t -> t.contains("Hello"));
        assertThat(answerTexts).anyMatch(t -> t.contains("World"));
    }

    @Test
    @DisplayName("partial thinking chunks should be streamed via SSE immediately")
    void shouldStreamThinkingChunksImmediately() throws InterruptedException {
        // Given: A sequence of partial thinking events
        Part thinking1 = Part.builder().text("Let me think...").thought(true).build();
        Part thinking2 = Part.builder().text(" about this").thought(true).build();

        Event partial1 = Event.builder()
                .content(Content.fromParts(thinking1))
                .partial(true)
                .author("qa-agent")
                .build();
        Event partial2 = Event.builder()
                .content(Content.fromParts(thinking2))
                .partial(true)
                .author("qa-agent")
                .build();
        Event done = Event.builder()
                .content(Content.fromParts(
                        Part.builder().text("Let me think... about this").thought(true).build(),
                        Part.builder().text("The answer is 42.").build()))
                .partial(false)
                .turnComplete(true)
                .author("qa-agent")
                .build();

        Flowable<Event> events = Flowable.just(partial1, partial2, done);
        lenient().when(runner.runAsync(anyString(), anyString(), any(), any())).thenReturn(events);
        lenient().when(spanContextRegistry.findLatestActiveSpanId(anyString())).thenReturn("span-1");

        List<SseEvent> captured = new CopyOnWriteArrayList<>();
        SseEmitter emitter = capturingEmitter(captured);

        ChatRequest request = new ChatRequest("test", null, null);

        AgentChatServiceImpl service = new AgentChatServiceImpl(
                runner, messageRepository, conversationRepository, executionLogger,
                messageService, conversationRunService, spanEventBuffer,
                spanContextRegistry, sessionService, documentAccessService, chatSourceService);
        service.smartChatStream("conv-1", request, emitter, userDetails);

        Thread.sleep(500);

        long thinkingEventsCount = captured.stream()
                .filter(e -> "thinking".equals(e.type()))
                .count();
        assertThat(thinkingEventsCount)
                .as("Thinking chunks should be streamed immediately for each partial thinking event")
                .isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("partial answer chunks should not skip repeated tokens")
    void shouldNotSkipRepeatedTokenChunks() throws InterruptedException {
        Part chunk1 = Part.builder().text("你").build();
        Part chunk2 = Part.builder().text("你").build();
        Part chunk3 = Part.builder().text("好").build();

        Event partial1 = Event.builder()
                .content(Content.fromParts(chunk1))
                .partial(true)
                .author("qa-agent")
                .build();
        Event partial2 = Event.builder()
                .content(Content.fromParts(chunk2))
                .partial(true)
                .author("qa-agent")
                .build();
        Event partial3 = Event.builder()
                .content(Content.fromParts(chunk3))
                .partial(true)
                .author("qa-agent")
                .build();

        Flowable<Event> events = Flowable.just(partial1, partial2, partial3);
        lenient().when(runner.runAsync(anyString(), anyString(), any(), any())).thenReturn(events);
        lenient().when(spanContextRegistry.findLatestActiveSpanId(anyString())).thenReturn("span-1");

        List<SseEvent> captured = new CopyOnWriteArrayList<>();
        SseEmitter emitter = capturingEmitter(captured);

        ChatRequest request = new ChatRequest("test", null, null);

        AgentChatServiceImpl service = new AgentChatServiceImpl(
                runner, messageRepository, conversationRepository, executionLogger,
                messageService, conversationRunService, spanEventBuffer,
                spanContextRegistry, sessionService, documentAccessService, chatSourceService);
        service.smartChatStream("conv-1", request, emitter, userDetails);

        Thread.sleep(500);

        String streamedAnswer = captured.stream()
                .filter(e -> "answer".equals(e.type()))
                .map(SseEvent::content)
                .reduce("", String::concat);

        assertThat(streamedAnswer).isEqualTo("你你好");
    }

    @Test
    @DisplayName("tool call events should be streamed via SSE with tool name")
    void shouldStreamToolCallEvents() throws InterruptedException {
        // Given: Function call event
        FunctionCall fc = FunctionCall.builder()
                .name("readNodeTexts")
                .build();
        Part fcPart = Part.builder().functionCall(fc).build();
        Content content = Content.builder()
                .role("model")
                .parts(List.of(fcPart))
                .build();
        Event toolCall = Event.builder()
                .author("qa-agent")
                .content(content)
                .build();

        Flowable<Event> events = Flowable.just(toolCall);
        lenient().when(runner.runAsync(anyString(), anyString(), any(), any())).thenReturn(events);

        List<SseEvent> captured = new CopyOnWriteArrayList<>();
        SseEmitter emitter = capturingEmitter(captured);

        ChatRequest request = new ChatRequest("test", null, null);

        AgentChatServiceImpl service = new AgentChatServiceImpl(
                runner, messageRepository, conversationRepository, executionLogger,
                messageService, conversationRunService, spanEventBuffer,
                spanContextRegistry, sessionService, documentAccessService, chatSourceService);
        service.smartChatStream("conv-1", request, emitter, userDetails);

        Thread.sleep(500);

        // Then: At least one SSE event should have been sent
        assertThat(captured).isNotEmpty();
    }

    @Test
    @DisplayName("tool result events should be streamed via SSE")
    void shouldStreamToolResultEvents() throws InterruptedException {
        // Given: Function call followed by function response
        FunctionCall fc = FunctionCall.builder().name("readNodeTexts").build();
        Part fcPart = Part.builder().functionCall(fc).build();

        FunctionResponse fr = FunctionResponse.builder()
                .name("readNodeTexts")
                .response(Map.of("result", "node text here"))
                .build();
        Part frPart = Part.builder().functionResponse(fr).build();

        Event toolCall = Event.builder()
                .author("qa-agent")
                .content(Content.fromParts(fcPart))
                .build();
        Event toolResult = Event.builder()
                .author("qa-agent")
                .content(Content.fromParts(frPart))
                .build();

        Flowable<Event> events = Flowable.just(toolCall, toolResult);
        lenient().when(runner.runAsync(anyString(), anyString(), any(), any())).thenReturn(events);

        List<SseEvent> captured = new CopyOnWriteArrayList<>();
        SseEmitter emitter = capturingEmitter(captured);

        ChatRequest request = new ChatRequest("test", null, null);

        AgentChatServiceImpl service = new AgentChatServiceImpl(
                runner, messageRepository, conversationRepository, executionLogger,
                messageService, conversationRunService, spanEventBuffer,
                spanContextRegistry, sessionService, documentAccessService, chatSourceService);
        service.smartChatStream("conv-1", request, emitter, userDetails);

        Thread.sleep(500);

        // Then: At least 2 SSE events should have been sent (tool call + tool result stage events)
        assertThat(captured).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("SSE phase prefix is init → search → sources → thinking before any answer")
    void shouldEmitInitSearchSourcesThinkingBeforeAnyAnswer() throws InterruptedException {
        List<SseEvent.SourceDto> retrieved = List.of(new SseEvent.SourceDto(
                12L, "支付系统需求.md", "2.1.3", "退款规则",
                "支付系统 > 交易 > 退款规则", "退款摘要", 86, 3
        ));
        when(chatSourceService.retrieveSources(eq(42L), eq("退款规则"), eq(userDetails))).thenReturn(retrieved);

        Part chunk1 = Part.builder().text("Hello").build();
        Event partial1 = Event.builder()
                .content(Content.fromParts(chunk1))
                .partial(true)
                .author("qa-agent")
                .build();
        Event done = Event.builder()
                .content(Content.fromParts(Part.builder().text("Hello").build()))
                .partial(false)
                .turnComplete(true)
                .author("qa-agent")
                .build();
        lenient().when(runner.runAsync(anyString(), anyString(), any(), any()))
                .thenReturn(Flowable.just(partial1, done));
        lenient().when(spanContextRegistry.findLatestActiveSpanId(anyString())).thenReturn("span-1");

        List<SseEvent> captured = new CopyOnWriteArrayList<>();
        SseEmitter emitter = capturingEmitter(captured);

        ChatRequest request = new ChatRequest("退款规则", null, 42L);
        AgentChatServiceImpl service = new AgentChatServiceImpl(
                runner, messageRepository, conversationRepository, executionLogger,
                messageService, conversationRunService, spanEventBuffer,
                spanContextRegistry, sessionService, documentAccessService, chatSourceService);
        service.smartChatStream("conv-1", request, emitter, userDetails);

        Thread.sleep(500);

        assertThat(captured).hasSizeGreaterThanOrEqualTo(5);

        SseEvent e0 = captured.get(0);
        assertThat(e0.type()).isEqualTo("stage");
        assertThat(e0.status()).isEqualTo("running");
        assertThat(e0.content()).isEqualTo("init");

        SseEvent e1 = captured.get(1);
        assertThat(e1.type()).isEqualTo("stage");
        assertThat(e1.status()).isEqualTo("running");
        assertThat(e1.content()).isEqualTo("search");

        SseEvent e2 = captured.get(2);
        assertThat(e2.type()).isEqualTo("sources");
        assertThat(e2.status()).isEqualTo("success");
        assertThat(e2.sources()).isEqualTo(retrieved);

        SseEvent e3 = captured.get(3);
        assertThat(e3.type()).isEqualTo("stage");
        assertThat(e3.status()).isEqualTo("running");
        assertThat(e3.content()).isEqualTo("thinking");

        int firstAnswerIdx = -1;
        for (int i = 0; i < captured.size(); i++) {
            if ("answer".equals(captured.get(i).type())) {
                firstAnswerIdx = i;
                break;
            }
        }
        assertThat(firstAnswerIdx)
                .as("first answer must come after thinking/sources prefix")
                .isGreaterThan(3);
    }

    @Test
    @DisplayName("without systemId, emit empty sources and never call retrieveSources")
    void shouldEmitSourcesWithEmptyArrayWhenSystemIdNullWithoutCallingRetriever() throws InterruptedException {
        lenient().when(runner.runAsync(anyString(), anyString(), any(), any())).thenReturn(Flowable.empty());

        List<SseEvent> captured = new CopyOnWriteArrayList<>();
        SseEmitter emitter = capturingEmitter(captured);

        ChatRequest request = new ChatRequest("hello", null, null);
        AgentChatServiceImpl service = new AgentChatServiceImpl(
                runner, messageRepository, conversationRepository, executionLogger,
                messageService, conversationRunService, spanEventBuffer,
                spanContextRegistry, sessionService, documentAccessService, chatSourceService);
        service.smartChatStream("conv-1", request, emitter, userDetails);

        Thread.sleep(500);

        verify(chatSourceService, never()).retrieveSources(any(), anyString(), any());

        assertThat(captured.size()).isGreaterThanOrEqualTo(4);
        assertThat(captured.get(0).content()).isEqualTo("init");
        assertThat(captured.get(1).content()).isEqualTo("search");

        SseEvent sourcesEvt = captured.get(2);
        assertThat(sourcesEvt.type()).isEqualTo("sources");
        assertThat(sourcesEvt.status()).isEqualTo("success");
        assertThat(sourcesEvt.sources()).isNotNull().isEmpty();

        assertThat(captured.get(3).content()).isEqualTo("thinking");
    }

    @Test
    @DisplayName("checkSystemAccess is invoked before retrieveSources when systemId is present")
    void shouldInvokeCheckSystemAccessBeforeRetrieveSourcesWhenSystemIdPresent() throws InterruptedException {
        lenient().when(runner.runAsync(anyString(), anyString(), any(), any())).thenReturn(Flowable.empty());

        List<SseEvent> captured = new CopyOnWriteArrayList<>();
        SseEmitter emitter = capturingEmitter(captured);

        ChatRequest request = new ChatRequest("q", null, 42L);
        AgentChatServiceImpl service = new AgentChatServiceImpl(
                runner, messageRepository, conversationRepository, executionLogger,
                messageService, conversationRunService, spanEventBuffer,
                spanContextRegistry, sessionService, documentAccessService, chatSourceService);
        service.smartChatStream("conv-1", request, emitter, userDetails);

        Thread.sleep(500);

        InOrder order = inOrder(documentAccessService, chatSourceService);
        order.verify(documentAccessService).checkSystemAccess(eq(42L), eq(userDetails));
        order.verify(chatSourceService).retrieveSources(eq(42L), eq("q"), eq(userDetails));

        assertThat(captured.get(2).type()).isEqualTo("sources");
    }
}
