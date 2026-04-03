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
 * 历史一致性检查器测试
 */
@ExtendWith(MockitoExtension.class)
class HistoryConsistencyCheckerImplTest {

    @Mock
    private LlmClient llmClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private HistoryConsistencyCheckerImpl historyConsistencyChecker;

    @BeforeEach
    void setUp() {
        historyConsistencyChecker = new HistoryConsistencyCheckerImpl(llmClient, objectMapper);
    }

    @Test
    void shouldPassHistoryConsistencyCheck() {
        // Given
        LlmMockFactory.mockForHistoryConsistencyCheck(llmClient, true);
        String requirement = "符合历史背景的需求";
        SystemKnowledgeTree tree = createEmptyTree();

        // When
        var result = historyConsistencyChecker.check(requirement, tree, null);

        // Then
        assertThat(result.report().riskLevel()).isEqualTo(RiskLevel.NONE);
        assertThat(result.report().category()).isEqualTo(EvaluationCategory.HISTORY);
    }

    @Test
    void shouldFailHistoryConsistencyCheck() {
        // Given
        LlmMockFactory.mockForHistoryConsistencyCheck(llmClient, false);
        String requirement = "违背历史背景的需求";
        SystemKnowledgeTree tree = createEmptyTree();

        // When
        var result = historyConsistencyChecker.check(requirement, tree, null);

        // Then
        assertThat(result.report().riskLevel()).isEqualTo(RiskLevel.MEDIUM);
        assertThat(result.report().category()).isEqualTo(EvaluationCategory.HISTORY);
    }

    @Test
    void shouldReturnErrorReport_whenLlmFails() {
        // Given
        LlmMockFactory.mockChatException(llmClient, new RuntimeException("LLM error"));
        String requirement = "测试需求";
        SystemKnowledgeTree tree = createEmptyTree();

        // When
        var result = historyConsistencyChecker.check(requirement, tree, null);

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
