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
 * 规则一致性检查器测试
 */
@ExtendWith(MockitoExtension.class)
class RuleConsistencyCheckerImplTest {

    @Mock
    private LlmClient llmClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private RuleConsistencyCheckerImpl ruleConsistencyChecker;

    @BeforeEach
    void setUp() {
        ruleConsistencyChecker = new RuleConsistencyCheckerImpl(llmClient, objectMapper);
    }

    @Test
    void shouldPassRuleConsistencyCheck() {
        // Given
        LlmMockFactory.mockForRuleConsistencyCheck(llmClient, true);
        String requirement = "遵循现有规则的需求";
        SystemKnowledgeTree tree = createEmptyTree();

        // When
        var result = ruleConsistencyChecker.check(requirement, tree, null);

        // Then
        assertThat(result.report().riskLevel()).isEqualTo(RiskLevel.NONE);
        assertThat(result.report().category()).isEqualTo(EvaluationCategory.CONSISTENCY);
    }

    @Test
    void shouldFailRuleConsistencyCheck() {
        // Given
        LlmMockFactory.mockForRuleConsistencyCheck(llmClient, false);
        String requirement = "违反现有规则的需求";
        SystemKnowledgeTree tree = createEmptyTree();

        // When
        var result = ruleConsistencyChecker.check(requirement, tree, null);

        // Then
        assertThat(result.report().riskLevel()).isEqualTo(RiskLevel.MEDIUM);
        assertThat(result.report().category()).isEqualTo(EvaluationCategory.CONSISTENCY);
    }

    @Test
    void shouldReturnErrorReport_whenLlmFails() {
        // Given
        LlmMockFactory.mockChatException(llmClient, new RuntimeException("LLM error"));
        String requirement = "测试需求";
        SystemKnowledgeTree tree = createEmptyTree();

        // When
        var result = ruleConsistencyChecker.check(requirement, tree, null);

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
