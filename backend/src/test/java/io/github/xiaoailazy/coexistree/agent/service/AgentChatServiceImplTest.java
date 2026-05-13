package io.github.xiaoailazy.coexistree.agent.service;

import com.google.adk.runner.Runner;
import io.github.xiaoailazy.coexistree.agent.observability.AgentExecutionLogger;
import io.github.xiaoailazy.coexistree.chat.dto.ChatRequest;
import io.github.xiaoailazy.coexistree.chat.dto.SseEvent;
import io.github.xiaoailazy.coexistree.chat.repository.ConversationRepository;
import io.github.xiaoailazy.coexistree.chat.repository.MessageRepository;
import io.github.xiaoailazy.coexistree.chat.service.ChatSourceService;
import io.github.xiaoailazy.coexistree.document.service.DocumentAccessService;
import io.github.xiaoailazy.coexistree.security.model.SecurityUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentChatServiceImplTest {

    @Mock
    private Runner runner;

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private AgentExecutionLogger executionLogger;

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
    private DocumentAccessService documentAccessService;

    @Mock
    private ChatSourceService chatSourceService;

    private AgentChatServiceImpl service;

    @BeforeEach
    void setUp() {
        lenient().when(userDetails.getId()).thenReturn(1L);
        lenient().when(userDetails.getUsername()).thenReturn("testuser");

        lenient().when(conversationRepository.findByConversationId(anyString()))
                .thenReturn(java.util.Optional.empty());

        lenient().when(conversationRunService.createRun(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new io.github.xiaoailazy.coexistree.observability.context.RunContext(
                        "run-test", "run-test", "conv-1", 1L,
                        1L, null, "root-agent", "corr-test", java.time.LocalDateTime.now()
                ));

        lenient().when(runner.runAsync(anyString(), anyString(), any(), any()))
                .thenReturn(io.reactivex.rxjava3.core.Flowable.empty());

        lenient().when(sessionService.createSession(anyString(), anyString(), any(), anyString()))
                .thenAnswer(inv -> io.reactivex.rxjava3.core.Single.just(
                        com.google.adk.sessions.Session.builder(inv.getArgument(3, String.class))
                                .appName(inv.getArgument(0, String.class))
                                .userId(inv.getArgument(1, String.class))
                                .state(inv.getArgument(2, java.util.concurrent.ConcurrentHashMap.class))
                                .build()));

        lenient().when(messageRepository.findLatestUserMessageId(anyString())).thenReturn(1L);
        lenient().doNothing().when(documentAccessService).checkSystemAccess(any(), any());

        service = new AgentChatServiceImpl(runner, messageRepository,
                conversationRepository, executionLogger, messageService,
                conversationRunService, spanEventBuffer, spanContextRegistry, sessionService,
                documentAccessService, chatSourceService);
    }

    private void awaitVirtualThread() throws InterruptedException {
        Thread.sleep(400);
    }

    @Test
    void shouldSaveUserMessageBeforeProcessing() throws InterruptedException {
        ChatRequest request = new ChatRequest("What is the system architecture?", null, null);
        SseEmitter emitter = mock(SseEmitter.class);

        service.smartChatStream("conv-1", request, emitter, userDetails);
        awaitVirtualThread();

        verify(messageService).saveUserMessage(eq("conv-1"), eq("What is the system architecture?"));
    }

    @Test
    void shouldSendErrorOnFailure() throws IOException, InterruptedException {
        ChatRequest request = new ChatRequest("test", null, null);

        doThrow(new RuntimeException("DB unavailable")).when(messageService).saveUserMessage(anyString(), anyString());

        SseEmitter emitter = mock(SseEmitter.class);

        service.smartChatStream("conv-1", request, emitter, userDetails);
        awaitVirtualThread();

        verify(emitter, atLeast(1)).send(isA(SseEmitter.SseEventBuilder.class));
        verify(emitter).complete();
    }

    @Test
    void shouldLogExecutionStart() throws InterruptedException {
        ChatRequest request = new ChatRequest("test", null, null);
        SseEmitter emitter = mock(SseEmitter.class);

        service.smartChatStream("conv-1", request, emitter, userDetails);
        awaitVirtualThread();

        verify(executionLogger).logStart("root-agent");
    }

    @Test
    void shouldResolveSystemIdFromRequest() throws InterruptedException {
        ChatRequest request = new ChatRequest("test", null, 42L);
        SseEmitter emitter = mock(SseEmitter.class);

        service.smartChatStream("conv-1", request, emitter, userDetails);
        awaitVirtualThread();

        verify(conversationRepository, never()).findByConversationId(anyString());
    }

    @Test
    void shouldResolveSystemIdFromConversationWhenNotInRequest() throws InterruptedException {
        ChatRequest request = new ChatRequest("test", null, null);
        SseEmitter emitter = mock(SseEmitter.class);

        var conv = new io.github.xiaoailazy.coexistree.chat.entity.ConversationEntity();
        conv.setConversationId("conv-1");
        conv.setSystemId(42L);
        conv.setCreatedAt(java.time.LocalDateTime.now());
        conv.setUpdatedAt(java.time.LocalDateTime.now());
        lenient().when(conversationRepository.findByConversationId("conv-1"))
                .thenReturn(java.util.Optional.of(conv));

        service.smartChatStream("conv-1", request, emitter, userDetails);
        awaitVirtualThread();

        verify(conversationRepository).findByConversationId("conv-1");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldStoreReadableSourceDocIdsInAgentSessionState() throws InterruptedException {
        ChatRequest request = new ChatRequest("退款规则", null, 42L);
        SseEmitter emitter = mock(SseEmitter.class);
        when(chatSourceService.retrieveSources(eq(42L), eq("退款规则"), eq(userDetails)))
                .thenReturn(List.of(
                        new SseEvent.SourceDto(12L, "支付系统需求.md", "2.1.3", "退款规则", "支付系统 > 交易 > 退款规则", "退款摘要", 86, 3),
                        new SseEvent.SourceDto(12L, "支付系统需求.md", "2.1.3", "退款规则", "支付系统 > 交易 > 退款规则", "退款摘要", 86, 3),
                        new SseEvent.SourceDto(13L, "订单.md", "1.2", "订单", "订单", "订单摘要", 10, 1)
                ));

        service.smartChatStream("conv-1", request, emitter, userDetails);
        awaitVirtualThread();

        ArgumentCaptor<ConcurrentHashMap<String, Object>> stateCaptor = ArgumentCaptor.forClass(ConcurrentHashMap.class);
        verify(sessionService).createSession(eq("coexistree"), anyString(), stateCaptor.capture(), eq("conv-1"));
        assertThat(stateCaptor.getValue().get("user:readableDocIds")).isEqualTo(List.of(12L, 13L));
    }

    @Test
    void shouldEmitSearchSourcesAndThinkingBeforeRunningAgent() throws IOException, InterruptedException {
        ChatRequest request = new ChatRequest("退款规则", null, 42L);
        SseEmitter emitter = mock(SseEmitter.class);
        when(chatSourceService.retrieveSources(eq(42L), eq("退款规则"), eq(userDetails)))
                .thenReturn(List.of(new SseEvent.SourceDto(
                        12L, "支付系统需求.md", "2.1.3", "退款规则",
                        "支付系统 > 交易 > 退款规则", "退款摘要", 86, 3
                )));

        service.smartChatStream("conv-1", request, emitter, userDetails);
        awaitVirtualThread();

        verify(chatSourceService).retrieveSources(42L, "退款规则", userDetails);
        verify(emitter, atLeast(4)).send(isA(SseEmitter.SseEventBuilder.class));
    }
}
