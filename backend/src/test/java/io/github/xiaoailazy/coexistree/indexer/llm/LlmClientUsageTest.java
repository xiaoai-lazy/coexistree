package io.github.xiaoailazy.coexistree.indexer.llm;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LlmClientUsageTest {

    @Test
    void llmResponseShouldCarryUsageInfo() {
        LlmClient.LlmResponse.Usage usage = new LlmClient.LlmResponse.Usage(
            100, 200, 300, 50
        );

        LlmClient.LlmResponse response = new LlmClient.LlmResponse(
            "resp_1", "hello", usage
        );

        assertThat(response.responseId()).isEqualTo("resp_1");
        assertThat(response.content()).isEqualTo("hello");
        assertThat(response.usage()).isNotNull();
        assertThat(response.usage().inputTokens()).isEqualTo(100);
        assertThat(response.usage().outputTokens()).isEqualTo(200);
        assertThat(response.usage().reasoningTokens()).isEqualTo(50);
    }

    @Test
    void llmResponseShouldSupportNullUsage() {
        LlmClient.LlmResponse response = new LlmClient.LlmResponse(
            "resp_2", "error response", null
        );

        assertThat(response.usage()).isNull();
    }
}
