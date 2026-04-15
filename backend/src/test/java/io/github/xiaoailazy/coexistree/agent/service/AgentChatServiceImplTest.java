package io.github.xiaoailazy.coexistree.agent.service;

import com.google.adk.agents.BaseAgent;
import io.github.xiaoailazy.coexistree.agent.observability.AgentExecutionLogger;
import io.github.xiaoailazy.coexistree.chat.dto.ChatRequest;
import io.github.xiaoailazy.coexistree.chat.entity.MessageEntity;
import io.github.xiaoailazy.coexistree.chat.repository.MessageRepository;
import io.github.xiaoailazy.coexistree.security.model.SecurityUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentChatServiceImplTest {

    @Mock
    private BaseAgent rootAgent;

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private AgentExecutionLogger executionLogger;

    @Mock
    private SecurityUserDetails userDetails;

    private AgentChatServiceImpl service;

    @BeforeEach
    void setUp() {
        lenient().when(userDetails.getId()).thenReturn(1L);

        service = new AgentChatServiceImpl(rootAgent, messageRepository, executionLogger);
    }

    @Test
    void shouldSaveUserMessageBeforeProcessing() {
        ChatRequest request = new ChatRequest("What is the system architecture?", null);

        when(messageRepository.findByConversationIdOrderByCreatedAt("conv-1"))
                .thenReturn(List.of());

        ArgumentCaptor<MessageEntity> messageCaptor = ArgumentCaptor.forClass(MessageEntity.class);
        when(messageRepository.save(messageCaptor.capture())).thenAnswer(i -> i.getArgument(0));

        SseEmitter emitter = mock(SseEmitter.class);

        service.smartChatStream("conv-1", request, emitter, userDetails);

        MessageEntity savedMsg = messageCaptor.getValue();
        assertThat(savedMsg.getConversationId()).isEqualTo("conv-1");
        assertThat(savedMsg.getRole()).isEqualTo("USER");
        assertThat(savedMsg.getContent()).isEqualTo("What is the system architecture?");
        assertThat(savedMsg.getCreatedAt()).isNotNull();
    }

    @Test
    void shouldSaveUserMessageWithHistory() {
        MessageEntity existingMsg = new MessageEntity();
        existingMsg.setConversationId("conv-1");
        existingMsg.setRole("USER");
        existingMsg.setContent("previous question");
        existingMsg.setCreatedAt(LocalDateTime.now().minusMinutes(1));

        when(messageRepository.findByConversationIdOrderByCreatedAt("conv-1"))
                .thenReturn(List.of(existingMsg));

        ArgumentCaptor<MessageEntity> messageCaptor = ArgumentCaptor.forClass(MessageEntity.class);
        when(messageRepository.save(messageCaptor.capture())).thenAnswer(i -> i.getArgument(0));

        SseEmitter emitter = mock(SseEmitter.class);
        ChatRequest request = new ChatRequest("follow up?", null);

        service.smartChatStream("conv-1", request, emitter, userDetails);

        MessageEntity savedMsg = messageCaptor.getValue();
        assertThat(savedMsg.getContent()).isEqualTo("follow up?");
    }

    @Test
    void shouldSendErrorOnFailure() throws IOException {
        ChatRequest request = new ChatRequest("test", null);

        // Force save to throw to trigger the error path
        when(messageRepository.save(any())).thenThrow(new RuntimeException("DB unavailable"));

        SseEmitter emitter = mock(SseEmitter.class);

        service.smartChatStream("conv-1", request, emitter, userDetails);

        // Verify error event was sent and emitter completed
        verify(emitter, atLeastOnce()).send(isA(SseEmitter.SseEventBuilder.class));
        verify(emitter).complete();
    }

    @Test
    void shouldLogExecutionStart() {
        ChatRequest request = new ChatRequest("test", null);
        when(messageRepository.findByConversationIdOrderByCreatedAt("conv-1"))
                .thenReturn(List.of());

        ArgumentCaptor<MessageEntity> messageCaptor = ArgumentCaptor.forClass(MessageEntity.class);
        when(messageRepository.save(messageCaptor.capture())).thenAnswer(i -> i.getArgument(0));

        SseEmitter emitter = mock(SseEmitter.class);

        service.smartChatStream("conv-1", request, emitter, userDetails);

        verify(executionLogger).logStart("root-agent");
    }

    @Test
    void shouldBuildContextFromRecentUserMessages() {
        List<MessageEntity> history = new ArrayList<>();
        MessageEntity m1 = new MessageEntity();
        m1.setRole("USER");
        m1.setContent("first question");
        m1.setCreatedAt(LocalDateTime.now().minusMinutes(5));

        MessageEntity m2 = new MessageEntity();
        m2.setRole("ASSISTANT");
        m2.setContent("first answer");
        m2.setCreatedAt(LocalDateTime.now().minusMinutes(4));

        MessageEntity m3 = new MessageEntity();
        m3.setRole("USER");
        m3.setContent("second question");
        m3.setCreatedAt(LocalDateTime.now().minusMinutes(3));

        history.add(m1);
        history.add(m2);
        history.add(m3);

        when(messageRepository.findByConversationIdOrderByCreatedAt("conv-1"))
                .thenReturn(history);

        ArgumentCaptor<MessageEntity> messageCaptor = ArgumentCaptor.forClass(MessageEntity.class);
        when(messageRepository.save(messageCaptor.capture())).thenAnswer(i -> i.getArgument(0));

        SseEmitter emitter = mock(SseEmitter.class);
        ChatRequest request = new ChatRequest("third question", null);

        service.smartChatStream("conv-1", request, emitter, userDetails);

        assertThat(messageCaptor.getValue().getContent()).isEqualTo("third question");
    }

    @Test
    void shouldBuildContextFromEmptyHistory() {
        when(messageRepository.findByConversationIdOrderByCreatedAt("conv-1"))
                .thenReturn(List.of());

        ArgumentCaptor<MessageEntity> messageCaptor = ArgumentCaptor.forClass(MessageEntity.class);
        when(messageRepository.save(messageCaptor.capture())).thenAnswer(i -> i.getArgument(0));

        SseEmitter emitter = mock(SseEmitter.class);
        ChatRequest request = new ChatRequest("only question", null);

        service.smartChatStream("conv-1", request, emitter, userDetails);

        assertThat(messageCaptor.getValue().getContent()).isEqualTo("only question");
    }
}
