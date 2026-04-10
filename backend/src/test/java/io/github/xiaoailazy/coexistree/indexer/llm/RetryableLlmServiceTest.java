package io.github.xiaoailazy.coexistree.indexer.llm;

import io.github.xiaoailazy.coexistree.indexer.model.TreeSearchResult;
import io.github.xiaoailazy.coexistree.knowledge.model.MergeInstruction;
import io.github.xiaoailazy.coexistree.knowledge.model.SystemTreeStructure;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RetryableLlmServiceTest {

    @Mock
    private LlmClient llmClient;

    @Mock
    private LlmResponseParser llmResponseParser;

    @Mock
    private LlmResponseValidator validator;

    @Mock
    private PromptTemplateService promptTemplateService;

    private RetryableLlmService retryableLlmService;

    @BeforeEach
    void setUp() {
        retryableLlmService = new RetryableLlmService(
            llmClient, llmResponseParser,validator
        );
    }

    @Test
    void treeSearch_shouldSucceedOnFirstAttempt() {
        String prompt = "test prompt";
        String validResponse = "{\"thinking\": \"test\", \"node_list\": []}";
        TreeSearchResult expectedResult = new TreeSearchResult();
        expectedResult.setThinking("test");
        expectedResult.setNodeList(List.of());

        when(llmClient.chat(eq(prompt), any(), anyDouble()))
            .thenReturn(new LlmClient.LlmResponse("resp_1", validResponse));
        when(validator.validateTreeSearch(validResponse))
            .thenReturn(LlmResponseValidator.ValidationResult.success());
        when(llmResponseParser.parseTreeSearch(validResponse))
            .thenReturn(expectedResult);

        TreeSearchResult result = retryableLlmService.treeSearch(prompt, null, 0.0);

        assertThat(result.getThinking()).isEqualTo("test");
        verify(llmClient, times(1)).chat(any(), any(), anyDouble());
    }

    @Test
    void treeSearch_shouldRetryOnValidationFailure() {
        String prompt = "test prompt";
        String invalidResponse = "invalid json";
        String validResponse = "{\"thinking\": \"test\", \"node_list\": []}";
        TreeSearchResult expectedResult = new TreeSearchResult();
        expectedResult.setThinking("test");

        when(llmClient.chat(any(), any(), anyDouble()))
            .thenReturn(new LlmClient.LlmResponse("resp_1", invalidResponse))
            .thenReturn(new LlmClient.LlmResponse("resp_2", validResponse));

        when(validator.validateTreeSearch(invalidResponse))
            .thenReturn(LlmResponseValidator.ValidationResult.failure("Invalid JSON", null));
        when(validator.validateTreeSearch(validResponse))
            .thenReturn(LlmResponseValidator.ValidationResult.success());

        when(llmResponseParser.parseTreeSearch(validResponse))
            .thenReturn(expectedResult);

        TreeSearchResult result = retryableLlmService.treeSearch(prompt, null, 0.0);

        assertThat(result.getThinking()).isEqualTo("test");
        verify(llmClient, times(2)).chat(any(), any(), anyDouble());
    }

    @Test
    void treeSearch_shouldThrowExceptionAfterMaxRetries() {
        String prompt = "test prompt";
        String invalidResponse = "invalid json";

        when(llmClient.chat(any(), any(), anyDouble()))
            .thenReturn(new LlmClient.LlmResponse("resp_1", invalidResponse));

        when(validator.validateTreeSearch(anyString()))
            .thenReturn(LlmResponseValidator.ValidationResult.failure("Invalid JSON", null));

        assertThatThrownBy(() -> retryableLlmService.treeSearch(prompt, null, 0.0))
            .isInstanceOf(RetryableLlmService.LlmRetryExhaustedException.class)
            .hasMessageContaining("failed after 3 attempts");

        verify(llmClient, times(3)).chat(any(), any(), anyDouble());
    }

    @Test
    void generateSystemTreeStructure_shouldSucceedOnFirstAttempt() {
        String prompt = "test prompt";
        String validResponse = "{\"structure\": [{\"title\": \"Root\"}]}";
        SystemTreeStructure expectedResult = new SystemTreeStructure();
        expectedResult.setStructure(List.of());

        when(llmClient.chat(eq(prompt), any(), anyDouble()))
            .thenReturn(new LlmClient.LlmResponse("resp_1", validResponse));
        when(validator.validateSystemTreeStructure(validResponse))
            .thenReturn(LlmResponseValidator.ValidationResult.success());
        when(llmResponseParser.parseSystemTreeStructure(validResponse))
            .thenReturn(expectedResult);

        SystemTreeStructure result = retryableLlmService.generateSystemTreeStructure(prompt, null, 0.0);

        assertThat(result).isNotNull();
        verify(llmClient, times(1)).chat(any(), any(), anyDouble());
    }

    @Test
    void generateMergeInstructions_shouldSucceedOnFirstAttempt() {
        String prompt = "test prompt";
        String validResponse = "[{\"operation\": \"UPDATE\", \"target_node_id\": \"1\", \"source_node_id\": \"2\"}]";
        MergeInstruction instruction = new MergeInstruction();
        instruction.setOperation("UPDATE");
        instruction.setTargetNodeId("1");
        instruction.setSourceNodeId("2");

        when(llmClient.chat(eq(prompt), any(), anyDouble()))
            .thenReturn(new LlmClient.LlmResponse("resp_1", validResponse));
        when(validator.validateMergeInstructions(validResponse))
            .thenReturn(LlmResponseValidator.ValidationResult.success());
        when(llmResponseParser.parseMergeInstructions(validResponse))
            .thenReturn(List.of(instruction));

        List<MergeInstruction> result = retryableLlmService.generateMergeInstructions(prompt, null, 0.0);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getOperation()).isEqualTo("UPDATE");
    }

    @Test
    void treeSearch_shouldRetryOnParseException() {
        String prompt = "test prompt";
        String validResponse = "{\"thinking\": \"test\", \"node_list\": []}";

        when(llmClient.chat(any(), any(), anyDouble()))
            .thenReturn(new LlmClient.LlmResponse("resp_1", validResponse))
            .thenReturn(new LlmClient.LlmResponse("resp_2", validResponse));

        when(validator.validateTreeSearch(validResponse))
            .thenReturn(LlmResponseValidator.ValidationResult.success());

        when(llmResponseParser.parseTreeSearch(validResponse))
            .thenThrow(new RuntimeException("Parse error"))
            .thenReturn(new TreeSearchResult());

        TreeSearchResult result = retryableLlmService.treeSearch(prompt, null, 0.0);

        assertThat(result).isNotNull();
        verify(llmClient, times(2)).chat(any(), any(), anyDouble());
    }

    @Test
    void treeSearchWithResponseId_shouldReturnResultWithResponseId() {
        String prompt = "test prompt";
        String validResponse = "{\"thinking\": \"test\", \"node_list\": []}";
        TreeSearchResult expectedResult = new TreeSearchResult();
        expectedResult.setThinking("test");

        when(llmClient.chat(eq(prompt), any(), anyDouble()))
            .thenReturn(new LlmClient.LlmResponse("resp_123", validResponse));
        when(validator.validateTreeSearch(validResponse))
            .thenReturn(LlmResponseValidator.ValidationResult.success());
        when(llmResponseParser.parseTreeSearch(validResponse))
            .thenReturn(expectedResult);

        RetryableLlmService.TreeSearchResultWithResponseId result =
            retryableLlmService.treeSearchWithResponseId(prompt, null, 0.0);

        assertThat(result.result().getThinking()).isEqualTo("test");
        assertThat(result.responseId()).isEqualTo("resp_123");
    }

    @Test
    void treeSearch_shouldHandleEmptyResponse() {
        String prompt = "test prompt";

        when(llmClient.chat(any(), any(), anyDouble()))
            .thenReturn(new LlmClient.LlmResponse("resp_1", ""));

        assertThatThrownBy(() -> retryableLlmService.treeSearch(prompt, null, 0.0))
            .isInstanceOf(RetryableLlmService.LlmRetryExhaustedException.class);
    }
}
