package io.github.xiaoailazy.coexistree.chat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.xiaoailazy.coexistree.shared.enums.ErrorCode;
import io.github.xiaoailazy.coexistree.shared.exception.BusinessException;
import io.github.xiaoailazy.coexistree.shared.util.JsonUtils;
import io.github.xiaoailazy.coexistree.config.JacksonConfig;
import io.github.xiaoailazy.coexistree.chat.dto.ConversationResponse;
import io.github.xiaoailazy.coexistree.chat.dto.MessageResponse;
import io.github.xiaoailazy.coexistree.chat.entity.ConversationEntity;
import io.github.xiaoailazy.coexistree.chat.entity.MessageEntity;
import io.github.xiaoailazy.coexistree.chat.repository.ConversationRepository;
import io.github.xiaoailazy.coexistree.chat.repository.MessageRepository;
import io.github.xiaoailazy.coexistree.document.repository.DocumentRepository;
import io.github.xiaoailazy.coexistree.document.service.DocumentTreeService;
import io.github.xiaoailazy.coexistree.document.storage.MarkdownFileStorageService;
import io.github.xiaoailazy.coexistree.review.service.IntentClassifier;
import io.github.xiaoailazy.coexistree.review.service.RequirementEvaluationService;
import io.github.xiaoailazy.coexistree.system.repository.SystemUserMappingRepository;
import io.github.xiaoailazy.coexistree.knowledge.service.SystemKnowledgeTreeService;
import io.github.xiaoailazy.coexistree.indexer.llm.LlmClient;
import io.github.xiaoailazy.coexistree.indexer.llm.PromptTemplateService;
import io.github.xiaoailazy.coexistree.shared.test.LlmMockFactory;
import io.github.xiaoailazy.coexistree.indexer.model.TreeSearchResult;
import io.github.xiaoailazy.coexistree.indexer.tree.TreeNodeMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationServiceTest {

    @Mock
    private ConversationRepository conversationRepository;
    @Mock
    private MessageRepository messageRepository;
    @Mock
    private SystemKnowledgeTreeService systemKnowledgeTreeService;
    @Mock
    private DocumentTreeService documentTreeService;
    @Mock
    private TreeSearchService treeSearchService;
    @Mock
    private IntentClassifier intentClassifier;
    @Mock
    private RequirementEvaluationService requirementEvaluationService;
    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private MarkdownFileStorageService markdownFileStorageService;
    @Mock
    private SystemUserMappingRepository systemUserMappingRepository;
    @Mock
    private AnswerGenerationService answerGenerationService;
    @Mock
    private PromptTemplateService promptTemplateService;
    @Mock
    private LlmClient llmClient;

    private final TreeNodeMapper treeNodeMapper = new TreeNodeMapper();
    private final ObjectMapper objectMapper = new JacksonConfig().objectMapper();
    private final JsonUtils jsonUtils = new JsonUtils(objectMapper);

    private ConversationServiceImpl conversationService;

    @BeforeEach
    void setUp() {
        conversationService = new ConversationServiceImpl(
                conversationRepository,
                messageRepository,
                systemKnowledgeTreeService,
                documentTreeService,
                treeNodeMapper,
                treeSearchService,
                answerGenerationService,
                promptTemplateService,
                llmClient,
                jsonUtils,
                intentClassifier,
                requirementEvaluationService,
                documentRepository,
                markdownFileStorageService,
                systemUserMappingRepository
        );
    }

    private ConversationEntity createTestConversation(Long systemId, String title) {
        ConversationEntity entity = new ConversationEntity();
        entity.setConversationId(UUID.randomUUID().toString());
        entity.setSystemId(systemId);
        entity.setTitle(title);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        return entity;
    }

    private MessageEntity createTestMessage(String conversationId, String role, String content) {
        MessageEntity msg = new MessageEntity();
        msg.setId(System.currentTimeMillis());
        msg.setConversationId(conversationId);
        msg.setRole(role);
        msg.setContent(content);
        msg.setCreatedAt(LocalDateTime.now());
        return msg;
    }

    @Test
    void testCreateConversation() {
        Long systemId = 1L;

        ConversationResponse response = conversationService.createConversation(systemId, null);

        assertThat(response).isNotNull();
        assertThat(response.conversationId()).isNotNull();
        assertThat(response.systemId()).isEqualTo(systemId);
        assertThat(response.title()).isNull();
        assertThat(response.createdAt()).isNotNull();
        assertThat(response.updatedAt()).isNotNull();

        verify(conversationRepository).save(any(ConversationEntity.class));
    }

    @Test
    void testCreateConversationWithTitle() {
        Long systemId = 1L;
        String title = "测试会话";

        ConversationResponse response = conversationService.createConversation(systemId, title);

        assertThat(response.title()).isEqualTo(title);
    }

    @Test
    void testListConversations() {
        ConversationEntity conv1 = createTestConversation(1L, "会话1");
        conv1.setUpdatedAt(LocalDateTime.now().minusHours(1));
        ConversationEntity conv2 = createTestConversation(1L, "会话2");
        conv2.setUpdatedAt(LocalDateTime.now());
        ConversationEntity conv3 = createTestConversation(1L, "会话3");
        conv3.setUpdatedAt(LocalDateTime.now().minusHours(2));

        when(conversationRepository.findAllByOrderByUpdatedAtDesc())
                .thenReturn(List.of(conv2, conv1, conv3));

        List<ConversationResponse> responses = conversationService.listConversations();

        assertThat(responses).hasSize(3);
        assertThat(responses.get(0).title()).isEqualTo("会话2");
        assertThat(responses.get(1).title()).isEqualTo("会话1");
        assertThat(responses.get(2).title()).isEqualTo("会话3");
    }

    @Test
    void testListConversationsEmpty() {
        when(conversationRepository.findAllByOrderByUpdatedAtDesc()).thenReturn(List.of());

        List<ConversationResponse> responses = conversationService.listConversations();

        assertThat(responses).isEmpty();
    }

    @Test
    void testGetConversation() {
        ConversationEntity entity = createTestConversation(1L, "测试会话");
        String conversationId = entity.getConversationId();

        when(conversationRepository.findByConversationId(conversationId))
                .thenReturn(Optional.of(entity));

        ConversationResponse response = conversationService.getConversation(conversationId);

        assertThat(response).isNotNull();
        assertThat(response.conversationId()).isEqualTo(conversationId);
        assertThat(response.title()).isEqualTo("测试会话");
    }

    @Test
    void testGetConversationNotFound() {
        String conversationId = "not-exist";

        when(conversationRepository.findByConversationId(conversationId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> conversationService.getConversation(conversationId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Conversation not found");
    }

    @Test
    void testGetMessages() {
        String conversationId = UUID.randomUUID().toString();
        ConversationEntity entity = createTestConversation(1L, null);
        entity.setConversationId(conversationId);

        MessageEntity msg1 = createTestMessage(conversationId, "USER", "问题1");
        MessageEntity msg2 = createTestMessage(conversationId, "ASSISTANT", "回答1");
        MessageEntity msg3 = createTestMessage(conversationId, "USER", "问题2");

        when(conversationRepository.findByConversationId(conversationId))
                .thenReturn(Optional.of(entity));
        when(messageRepository.findByConversationIdOrderByCreatedAt(conversationId))
                .thenReturn(List.of(msg1, msg2, msg3));

        List<MessageResponse> messages = conversationService.getMessages(conversationId);

        assertThat(messages).hasSize(3);
        assertThat(messages.get(0).role()).isEqualTo("USER");
        assertThat(messages.get(1).role()).isEqualTo("ASSISTANT");
        assertThat(messages.get(2).role()).isEqualTo("USER");
    }

    @Test
    void testGetMessagesEmpty() {
        String conversationId = UUID.randomUUID().toString();
        ConversationEntity entity = createTestConversation(1L, null);
        entity.setConversationId(conversationId);

        when(conversationRepository.findByConversationId(conversationId))
                .thenReturn(Optional.of(entity));
        when(messageRepository.findByConversationIdOrderByCreatedAt(conversationId))
                .thenReturn(List.of());

        List<MessageResponse> messages = conversationService.getMessages(conversationId);

        assertThat(messages).isEmpty();
    }

    @Test
    void testDeleteConversation() {
        String conversationId = UUID.randomUUID().toString();
        ConversationEntity entity = createTestConversation(1L, null);
        entity.setConversationId(conversationId);

        when(conversationRepository.findByConversationId(conversationId))
                .thenReturn(Optional.of(entity));

        conversationService.deleteConversation(conversationId);

        verify(messageRepository).deleteByConversationId(conversationId);
        verify(conversationRepository).delete(entity);
    }

    @Test
    void testDeleteConversationNotFound() {
        String conversationId = "not-exist";

        when(conversationRepository.findByConversationId(conversationId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> conversationService.deleteConversation(conversationId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Conversation not found");
    }

    @Test
    void testGenerateTitle() {
        String conversationId = UUID.randomUUID().toString();
        ConversationEntity entity = createTestConversation(1L, null);
        entity.setConversationId(conversationId);

        MessageEntity msg1 = createTestMessage(conversationId, "USER", "如何部署？");
        MessageEntity msg2 = createTestMessage(conversationId, "ASSISTANT", "部署步骤如下...");

        when(conversationRepository.findByConversationId(conversationId))
                .thenReturn(Optional.of(entity));
        when(messageRepository.findByConversationIdOrderByCreatedAt(conversationId))
                .thenReturn(List.of(msg1, msg2));
        when(promptTemplateService.buildTitleGenerationPrompt(anyList()))
                .thenReturn("prompt");
        LlmMockFactory.mockForTitleGeneration(llmClient, "部署问题");

        String title = conversationService.generateTitle(conversationId);

        assertThat(title).isEqualTo("部署问题");
        verify(conversationRepository).save(any(ConversationEntity.class));
    }

    @Test
    void testGenerateTitleTruncate() {
        String conversationId = UUID.randomUUID().toString();
        ConversationEntity entity = createTestConversation(1L, null);
        entity.setConversationId(conversationId);

        MessageEntity msg = createTestMessage(conversationId, "USER", "问题");

        when(conversationRepository.findByConversationId(conversationId))
                .thenReturn(Optional.of(entity));
        when(messageRepository.findByConversationIdOrderByCreatedAt(conversationId))
                .thenReturn(List.of(msg));
        when(promptTemplateService.buildTitleGenerationPrompt(anyList()))
                .thenReturn("prompt");
        LlmMockFactory.mockForTitleGeneration(llmClient, "这是一个非常非常长的标题超过了十个字");

        String title = conversationService.generateTitle(conversationId);

        assertThat(title).hasSize(10);
    }

    @Test
    void testGenerateTitleNoMessages() {
        String conversationId = UUID.randomUUID().toString();
        ConversationEntity entity = createTestConversation(1L, null);
        entity.setConversationId(conversationId);

        when(conversationRepository.findByConversationId(conversationId))
                .thenReturn(Optional.of(entity));
        when(messageRepository.findByConversationIdOrderByCreatedAt(conversationId))
                .thenReturn(List.of());

        String title = conversationService.generateTitle(conversationId);

        assertThat(title).isEqualTo("新对话");
        verify(llmClient, never()).chat(anyString(), any(), any(Double.class));
    }

    @Test
    void testGenerateTitleOnlyAssistantMessages() {
        String conversationId = UUID.randomUUID().toString();
        ConversationEntity entity = createTestConversation(1L, null);
        entity.setConversationId(conversationId);

        MessageEntity msg = createTestMessage(conversationId, "ASSISTANT", "这是回答");

        when(conversationRepository.findByConversationId(conversationId))
                .thenReturn(Optional.of(entity));
        when(messageRepository.findByConversationIdOrderByCreatedAt(conversationId))
                .thenReturn(List.of(msg));

        String title = conversationService.generateTitle(conversationId);

        assertThat(title).isEqualTo("新对话");
        verify(llmClient, never()).chat(anyString(), any(), any(Double.class));
    }

    @Test
    void testGenerateTitleFallback() {
        String conversationId = UUID.randomUUID().toString();
        ConversationEntity entity = createTestConversation(1L, null);
        entity.setConversationId(conversationId);

        MessageEntity msg = createTestMessage(conversationId, "USER", "如何部署 Kubernetes？");

        when(conversationRepository.findByConversationId(conversationId))
                .thenReturn(Optional.of(entity));
        when(messageRepository.findByConversationIdOrderByCreatedAt(conversationId))
                .thenReturn(List.of(msg));
        when(promptTemplateService.buildTitleGenerationPrompt(anyList()))
                .thenReturn("prompt");
        LlmMockFactory.mockChatException(llmClient, new RuntimeException("LLM error"));

        String title = conversationService.generateTitle(conversationId);

        assertThat(title).isEqualTo("如何部署 Kuber");
        verify(conversationRepository).save(any(ConversationEntity.class));
    }

    @Test
    void testGenerateTitleLimitFive() {
        String conversationId = UUID.randomUUID().toString();
        ConversationEntity entity = createTestConversation(1L, null);
        entity.setConversationId(conversationId);

        List<MessageEntity> messages = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            messages.add(createTestMessage(conversationId, "USER", "问题" + i));
            messages.add(createTestMessage(conversationId, "ASSISTANT", "回答" + i));
        }

        when(conversationRepository.findByConversationId(conversationId))
                .thenReturn(Optional.of(entity));
        when(messageRepository.findByConversationIdOrderByCreatedAt(conversationId))
                .thenReturn(messages);
        when(promptTemplateService.buildTitleGenerationPrompt(anyList()))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    List<MessageEntity> msgs = invocation.getArgument(0);
                    assertThat(msgs).hasSize(5);
                    return "prompt";
                });
        LlmMockFactory.mockForTitleGeneration(llmClient, "标题");

        conversationService.generateTitle(conversationId);
    }

    @Test
    void testUpdateTitle() {
        String conversationId = UUID.randomUUID().toString();
        ConversationEntity entity = createTestConversation(1L, null);
        entity.setConversationId(conversationId);

        when(conversationRepository.findByConversationId(conversationId))
                .thenReturn(Optional.of(entity));

        conversationService.updateTitle(conversationId, "手动标题");

        ArgumentCaptor<ConversationEntity> captor = ArgumentCaptor.forClass(ConversationEntity.class);
        verify(conversationRepository).save(captor.capture());
        assertThat(captor.getValue().getTitle()).isEqualTo("手动标题");
    }

}
