package io.github.xiaoailazy.coexistree.review.service.detector.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.xiaoailazy.coexistree.indexer.llm.LlmClient;
import io.github.xiaoailazy.coexistree.knowledge.model.SystemKnowledgeTree;
import io.github.xiaoailazy.coexistree.review.enums.EvaluationCategory;
import io.github.xiaoailazy.coexistree.review.enums.RiskLevel;
import io.github.xiaoailazy.coexistree.shared.test.LlmMockFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 冲突检测器测试
 */
@ExtendWith(MockitoExtension.class)
class ConflictDetectorImplTest {

    @Mock
    private LlmClient llmClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private ConflictDetectorImpl conflictDetector;

    @BeforeEach
    void setUp() {
        conflictDetector = new ConflictDetectorImpl(llmClient, objectMapper);
    }

    @Test
    void shouldDetectHighRiskConflict() {
        // Given
        LlmMockFactory.mockForConflictDetection(llmClient, "HIGH");
        String requirement = "新增用户管理功能";
        SystemKnowledgeTree tree = createEmptyTree();

        // When
        var result = conflictDetector.detect(requirement, tree, null);

        // Then
        assertThat(result.report().riskLevel()).isEqualTo(RiskLevel.HIGH);
        assertThat(result.report().category()).isEqualTo(EvaluationCategory.CONFLICT);
        assertThat(result.report().details()).hasSize(1);
    }

    @Test
    void shouldDetectNoConflict() {
        // Given
        LlmMockFactory.mockForConflictDetectionNoConflict(llmClient);
        String requirement = "新增独立功能模块";
        SystemKnowledgeTree tree = createEmptyTree();

        // When
        var result = conflictDetector.detect(requirement, tree, null);

        // Then
        assertThat(result.report().riskLevel()).isEqualTo(RiskLevel.NONE);
        assertThat(result.report().details()).isEmpty();
    }

    @Test
    void shouldReturnErrorReport_whenLlmFails() {
        // Given
        LlmMockFactory.mockChatException(llmClient, new RuntimeException("LLM error"));
        String requirement = "测试需求";
        SystemKnowledgeTree tree = createEmptyTree();

        // When
        var result = conflictDetector.detect(requirement, tree, null);

        // Then
        assertThat(result.report().riskLevel()).isEqualTo(RiskLevel.NONE);
        assertThat(result.report().summary()).contains("异常");
    }

    private SystemKnowledgeTree createEmptyTree() {
        SystemKnowledgeTree tree = new SystemKnowledgeTree();
        tree.setSystemId(1L);
        tree.setSystemCode("test-system");
        tree.setStructure(new ArrayList<>());
        return tree;
    }
}
