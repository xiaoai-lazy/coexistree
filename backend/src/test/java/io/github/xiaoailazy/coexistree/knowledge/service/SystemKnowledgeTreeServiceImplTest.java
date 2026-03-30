package io.github.xiaoailazy.coexistree.knowledge.service;

import io.github.xiaoailazy.coexistree.shared.enums.ErrorCode;
import io.github.xiaoailazy.coexistree.shared.exception.BusinessException;
import io.github.xiaoailazy.coexistree.indexer.summary.NodeSummaryService;
import io.github.xiaoailazy.coexistree.shared.repository.ProcessLogRepository;
import io.github.xiaoailazy.coexistree.shared.util.JsonUtils;
import io.github.xiaoailazy.coexistree.config.AppStorageProperties;
import io.github.xiaoailazy.coexistree.knowledge.entity.SystemKnowledgeTreeEntity;
import io.github.xiaoailazy.coexistree.knowledge.model.SystemKnowledgeTree;
import io.github.xiaoailazy.coexistree.knowledge.repository.SystemKnowledgeTreeRepository;
import io.github.xiaoailazy.coexistree.knowledge.storage.SystemTreeFileLoader;
import io.github.xiaoailazy.coexistree.knowledge.storage.SystemTreeFileWriter;
import io.github.xiaoailazy.coexistree.indexer.llm.LlmClient;
import io.github.xiaoailazy.coexistree.indexer.llm.LlmResponseParser;
import io.github.xiaoailazy.coexistree.indexer.llm.PromptTemplateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SystemKnowledgeTreeServiceImplTest {

    private SystemKnowledgeTreeRepository repository;
    private SystemTreeFileLoader fileLoader;
    private SystemKnowledgeTreeServiceImpl service;

    @BeforeEach
    void setUp() {
        repository = mock(SystemKnowledgeTreeRepository.class);
        fileLoader = mock(SystemTreeFileLoader.class);
        SystemTreeFileWriter fileWriter = mock(SystemTreeFileWriter.class);
        PromptTemplateService promptService = mock(PromptTemplateService.class);
        LlmClient llmClient = mock(LlmClient.class);
        LlmResponseParser responseParser = mock(LlmResponseParser.class);
        JsonUtils jsonUtils = mock(JsonUtils.class);
        AppStorageProperties storageProps = mock(AppStorageProperties.class);
        ProcessLogRepository processLogRepo = mock(ProcessLogRepository.class);
        NodeSummaryService nodeSummaryService = mock(NodeSummaryService.class);
        SnapshotService snapshotService = mock(SnapshotService.class);
        service = new SystemKnowledgeTreeServiceImpl(repository, fileLoader, fileWriter,
                promptService, llmClient, responseParser, jsonUtils, storageProps, processLogRepo,
                nodeSummaryService, snapshotService);
    }

    @Test
    void shouldGetActiveTreeSuccessfully() {
        // Given
        Long systemId = 1L;
        SystemKnowledgeTreeEntity entity = createActiveTreeEntity(systemId);
        SystemKnowledgeTree expectedTree = createTestTree();

        when(repository.findBySystemId(systemId)).thenReturn(Optional.of(entity));
        when(fileLoader.load(any(Path.class))).thenReturn(expectedTree);

        // When
        SystemKnowledgeTree result = service.getActiveTree(systemId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getSystemId()).isEqualTo(expectedTree.getSystemId());
        assertThat(result.getSystemCode()).isEqualTo(expectedTree.getSystemCode());
    }

    @Test
    void shouldThrowExceptionWhenTreeNotFound() {
        // Given
        Long systemId = 1L;
        when(repository.findBySystemId(systemId)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> service.getActiveTree(systemId))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SYSTEM_TREE_NOT_FOUND)
            .hasMessageContaining("System knowledge tree not found");
    }

    @Test
    void shouldThrowExceptionWhenTreeStatusIsNotActive() {
        // Given
        Long systemId = 1L;
        SystemKnowledgeTreeEntity entity = createActiveTreeEntity(systemId);
        entity.setTreeStatus("BUILDING");

        when(repository.findBySystemId(systemId)).thenReturn(Optional.of(entity));

        // When/Then
        assertThatThrownBy(() -> service.getActiveTree(systemId))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SYSTEM_TREE_NOT_READY)
            .hasMessageContaining("System knowledge tree is not ready");
    }

    @Test
    void shouldThrowExceptionWhenTreeStatusIsEmpty() {
        // Given
        Long systemId = 1L;
        SystemKnowledgeTreeEntity entity = createActiveTreeEntity(systemId);
        entity.setTreeStatus("EMPTY");

        when(repository.findBySystemId(systemId)).thenReturn(Optional.of(entity));

        // When/Then
        assertThatThrownBy(() -> service.getActiveTree(systemId))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SYSTEM_TREE_NOT_READY);
    }

    private SystemKnowledgeTreeEntity createActiveTreeEntity(Long systemId) {
        SystemKnowledgeTreeEntity entity = new SystemKnowledgeTreeEntity();
        entity.setId(1L);
        entity.setSystemId(systemId);
        entity.setTreeFilePath("data/system-trees/test/system_tree.json");
        entity.setTreeVersion(1);
        entity.setNodeCount(10);
        entity.setTreeStatus("ACTIVE");
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        return entity;
    }

    private SystemKnowledgeTree createTestTree() {
        SystemKnowledgeTree tree = new SystemKnowledgeTree();
        tree.setSystemId(1L);
        tree.setSystemCode("test");
        tree.setSystemName("Test System");
        tree.setTreeVersion(1);
        tree.setCreatedAt(LocalDateTime.now());
        tree.setLastUpdatedAt(LocalDateTime.now());
        return tree;
    }
}
