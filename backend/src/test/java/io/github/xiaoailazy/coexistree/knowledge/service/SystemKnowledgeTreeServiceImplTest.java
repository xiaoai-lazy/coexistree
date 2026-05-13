package io.github.xiaoailazy.coexistree.knowledge.service;

import io.github.xiaoailazy.coexistree.indexer.llm.LlmClient;
import io.github.xiaoailazy.coexistree.indexer.llm.PromptTemplateService;
import io.github.xiaoailazy.coexistree.indexer.llm.RetryableLlmService;
import io.github.xiaoailazy.coexistree.indexer.summary.NodeSummaryService;
import io.github.xiaoailazy.coexistree.indexer.tree.TreeSimplifier;
import io.github.xiaoailazy.coexistree.knowledge.entity.SystemKnowledgeTreeEntity;
import io.github.xiaoailazy.coexistree.knowledge.model.SystemKnowledgeTree;
import io.github.xiaoailazy.coexistree.knowledge.repository.SystemKnowledgeTreeRepository;
import io.github.xiaoailazy.coexistree.shared.enums.ErrorCode;
import io.github.xiaoailazy.coexistree.shared.exception.BusinessException;
import io.github.xiaoailazy.coexistree.shared.repository.DocProcessLogRepository;
import io.github.xiaoailazy.coexistree.shared.util.JsonUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * SystemKnowledgeTreeServiceImpl 单元测试
 *
 * 本测试专注于验证 getActiveTree 核心查询功能。
 * mergeBaseline 和 mergeChange 方法涉及复杂的 LLM 调用链，
 * 这些更适合用集成测试覆盖。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SystemKnowledgeTreeServiceImpl 测试")
class SystemKnowledgeTreeServiceImplTest {

    @Mock
    private SystemKnowledgeTreeRepository repository;
    @Mock
    private PromptTemplateService promptTemplateService;
    @Mock
    private LlmClient llmClient;
    @Mock
    private RetryableLlmService retryableLlmService;
    @Mock
    private JsonUtils jsonUtils;
    @Mock
    private DocProcessLogRepository processLogRepository;
    @Mock
    private NodeSummaryService nodeSummaryService;
    @Mock
    private SnapshotService snapshotService;
    @Mock
    private TreeSimplifier treeSimplifier;

    private SystemKnowledgeTreeServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SystemKnowledgeTreeServiceImpl(
                repository, promptTemplateService,
                llmClient, retryableLlmService, jsonUtils,
                processLogRepository, nodeSummaryService, snapshotService,
                treeSimplifier
        );
    }

    @Nested
    @DisplayName("getActiveTree 测试")
    class GetActiveTreeTests {

        @Test
        @DisplayName("成功获取活跃知识树")
        void shouldGetActiveTreeSuccessfully() {
            Long systemId = 1L;
            SystemKnowledgeTreeEntity entity = createActiveTreeEntity(systemId);
            SystemKnowledgeTree expectedTree = createTestTree();

            when(repository.findBySystemId(systemId)).thenReturn(Optional.of(entity));
            when(jsonUtils.fromJson(entity.getTreeJson(), SystemKnowledgeTree.class)).thenReturn(expectedTree);

            SystemKnowledgeTree result = service.getActiveTree(systemId);

            assertThat(result).isNotNull();
            assertThat(result.getSystemId()).isEqualTo(expectedTree.getSystemId());
            assertThat(result.getSystemCode()).isEqualTo(expectedTree.getSystemCode());
        }

        @Test
        @DisplayName("知识树不存在时抛出异常")
        void shouldThrowExceptionWhenTreeNotFound() {
            Long systemId = 1L;
            when(repository.findBySystemId(systemId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getActiveTree(systemId))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SYSTEM_TREE_NOT_FOUND)
                    .hasMessageContaining("System knowledge tree not found");
        }

        @Test
        @DisplayName("知识树状态为 BUILDING 时抛出异常")
        void shouldThrowExceptionWhenTreeStatusIsBuilding() {
            Long systemId = 1L;
            SystemKnowledgeTreeEntity entity = createActiveTreeEntity(systemId);
            entity.setTreeStatus("BUILDING");

            when(repository.findBySystemId(systemId)).thenReturn(Optional.of(entity));

            assertThatThrownBy(() -> service.getActiveTree(systemId))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SYSTEM_TREE_NOT_READY)
                    .hasMessageContaining("System knowledge tree is not ready");
        }

        @Test
        @DisplayName("知识树状态为 EMPTY 时抛出异常")
        void shouldThrowExceptionWhenTreeStatusIsEmpty() {
            Long systemId = 1L;
            SystemKnowledgeTreeEntity entity = createActiveTreeEntity(systemId);
            entity.setTreeStatus("EMPTY");

            when(repository.findBySystemId(systemId)).thenReturn(Optional.of(entity));

            assertThatThrownBy(() -> service.getActiveTree(systemId))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SYSTEM_TREE_NOT_READY);
        }

        @Test
        @DisplayName("知识树状态为 null 时抛出异常")
        void shouldThrowExceptionWhenTreeStatusIsNull() {
            Long systemId = 1L;
            SystemKnowledgeTreeEntity entity = createActiveTreeEntity(systemId);
            entity.setTreeStatus(null);

            when(repository.findBySystemId(systemId)).thenReturn(Optional.of(entity));

            assertThatThrownBy(() -> service.getActiveTree(systemId))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SYSTEM_TREE_NOT_READY);
        }
    }

    private SystemKnowledgeTreeEntity createActiveTreeEntity(Long systemId) {
        SystemKnowledgeTreeEntity entity = new SystemKnowledgeTreeEntity();
        entity.setId(1L);
        entity.setSystemId(systemId);
        entity.setTreeJson("{\"structure\":[]}");
        entity.setTreeVersion(1);
        entity.setNodeCount(10);
        entity.setTreeStatus("ACTIVE");
        entity.setDescription("Test description");
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        return entity;
    }

    private SystemKnowledgeTree createTestTree() {
        SystemKnowledgeTree tree = new SystemKnowledgeTree();
        tree.setSystemId(1L);
        tree.setSystemCode("test-sys");
        tree.setSystemName("Test System");
        tree.setTreeVersion(1);
        tree.setDescription("Test description");
        tree.setCreatedAt(LocalDateTime.now());
        tree.setLastUpdatedAt(LocalDateTime.now());
        tree.setStructure(new ArrayList<>());
        return tree;
    }
}
