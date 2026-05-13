package io.github.xiaoailazy.coexistree.agent.service;

import com.google.adk.runner.Runner;
import io.github.xiaoailazy.coexistree.agent.observability.AgentExecutionLogger;
import io.github.xiaoailazy.coexistree.agent.session.DatabaseSessionService;
import io.github.xiaoailazy.coexistree.chat.repository.ConversationRepository;
import io.github.xiaoailazy.coexistree.chat.repository.MessageRepository;
import io.github.xiaoailazy.coexistree.chat.service.ChatSourceService;
import io.github.xiaoailazy.coexistree.chat.service.MessageService;
import io.github.xiaoailazy.coexistree.document.service.DocumentAccessService;
import io.github.xiaoailazy.coexistree.observability.context.SpanContextRegistry;
import io.github.xiaoailazy.coexistree.observability.service.ConversationRunService;
import io.github.xiaoailazy.coexistree.observability.service.SpanEventBuffer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AgentChatServiceImplConstructorTest {

    @Test
    void constructorAcceptsSourcePermissionDependencies() {
        AgentChatServiceImpl service = new AgentChatServiceImpl(
                mock(Runner.class),
                mock(MessageRepository.class),
                mock(ConversationRepository.class),
                mock(AgentExecutionLogger.class),
                mock(MessageService.class),
                mock(ConversationRunService.class),
                mock(SpanEventBuffer.class),
                mock(SpanContextRegistry.class),
                mock(DatabaseSessionService.class),
                mock(DocumentAccessService.class),
                mock(ChatSourceService.class)
        );

        assertThat(service).isNotNull();
    }
}
