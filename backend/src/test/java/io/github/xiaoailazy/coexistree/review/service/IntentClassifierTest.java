package io.github.xiaoailazy.coexistree.review.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.xiaoailazy.coexistree.indexer.llm.LlmClient;
import io.github.xiaoailazy.coexistree.review.enums.IntentType;
import io.github.xiaoailazy.coexistree.shared.test.LlmMockFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 意图分类器测试
 */
@ExtendWith(MockitoExtension.class)
class IntentClassifierTest {

    @Mock
    private LlmClient llmClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private IntentClassifier intentClassifier;

    @BeforeEach
    void setUp() {
        intentClassifier = new IntentClassifier(llmClient, objectMapper);
    }

    @Test
    void shouldClassifyQuestionIntent_withQuickKeyword() {
        // Given - 使用关键词快速分类
        String question = "如何部署这个系统？";

        // When
        var result = intentClassifier.classify(question, false);

        // Then
        assertThat(result.intent()).isEqualTo(IntentType.QUESTION);
        assertThat(result.confidence().name()).isEqualTo("HIGH");
    }

    @Test
    void shouldClassifyEvalIntent_withQuickKeyword() {
        // Given - 使用关键词快速分类
        String question = "评估一下这个需求的影响";

        // When
        var result = intentClassifier.classify(question, true);

        // Then
        assertThat(result.intent()).isEqualTo(IntentType.REQUIREMENT_EVAL);
        assertThat(result.confidence().name()).isEqualTo("HIGH");
    }

    @Test
    void shouldClassifyQuestionIntent_usingLlm() {
        // Given - 需要使用 LLM 分类
        LlmMockFactory.mockForIntentClassificationQuestion(llmClient);
        String question = "随便问点什么";

        // When
        var result = intentClassifier.classify(question, false);

        // Then
        assertThat(result.intent()).isEqualTo(IntentType.QUESTION);
        assertThat(result.confidence().name()).isEqualTo("HIGH");
    }

    @Test
    void shouldClassifyEvalIntent_usingLlm() {
        // Given - 需要使用 LLM 分类
        LlmMockFactory.mockForIntentClassificationEval(llmClient);
        String question = "评估需求";

        // When
        var result = intentClassifier.classify(question, true);

        // Then
        assertThat(result.intent()).isEqualTo(IntentType.REQUIREMENT_EVAL);
        assertThat(result.confidence().name()).isEqualTo("HIGH");
    }

    @Test
    void shouldReturnQuestionFallback_whenLlmFails() {
        // Given - LLM 调用失败
        LlmMockFactory.mockChatException(llmClient, new RuntimeException("LLM error"));
        String question = "测试问题";

        // When
        var result = intentClassifier.classify(question, false);

        // Then - 返回默认问答意图
        assertThat(result.intent()).isEqualTo(IntentType.QUESTION);
        assertThat(result.confidence().name()).isEqualTo("HIGH");
    }
}
