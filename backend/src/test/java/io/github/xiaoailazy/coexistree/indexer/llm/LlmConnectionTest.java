package io.github.xiaoailazy.coexistree.indexer.llm;

import io.github.xiaoailazy.coexistree.config.LlmProperties;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@SpringBootTest
class LlmConnectionTest {

    @Autowired
    private LlmClient llmClient;

    @Autowired
    private LlmProperties llmProperties;

    @BeforeEach
    void setUp() {
        // 通过环境变量或系统属性 LLM_TEST_ENABLED=true 启用真实 LLM 测试
        String enabled = System.getenv("LLM_TEST_ENABLED");
        if (enabled == null) {
            enabled = System.getProperty("LLM_TEST_ENABLED");
        }
        boolean shouldRun = "true".equalsIgnoreCase(enabled);

        log.info("LLM_TEST_ENABLED={}, should run tests={}", enabled, shouldRun);
        Assumptions.assumeTrue(shouldRun, "Skipping LLM tests. Set LLM_TEST_ENABLED=true to enable.");
    }

    @Test
    void shouldConnectToLlmService() {
        log.info("Testing LLM connection with baseUrl={}, model={}",
                llmProperties.getBaseUrl(), llmProperties.getModel());

        LlmClient.LlmResponse response = llmClient.chat(
                "Hello, please respond with a simple 'pong'",
                null,
                0.3
        );

        log.info("Response ID: {}", response.responseId());
        log.info("Response content: {}", response.content());

        assertThat(response.content())
                .as("LLM should return a non-empty response")
                .isNotBlank();

        assertThat(response.content().toLowerCase())
                .as("Response should contain 'pong'")
                .contains("pong");
    }

    @Test
    void shouldStreamResponseFromLlm() {
        log.info("Testing LLM streaming with baseUrl={}, model={}",
                llmProperties.getBaseUrl(), llmProperties.getModel());

        StringBuilder receivedText = new StringBuilder();

        String responseId = llmClient.chatStream(
                "Say 'streaming works' and nothing else",
                null,
                0.3,
                null,
                thinking -> log.debug("Thinking: {}", thinking),
                text -> {
                    receivedText.append(text);
                    log.debug("Received chunk: {}", text);
                }
        );

        String fullResponse = receivedText.toString();
        log.info("Response ID: {}", responseId);
        log.info("Full streaming response: {}", fullResponse);

        assertThat(fullResponse)
                .as("Streaming should return non-empty content")
                .isNotBlank();

        assertThat(fullResponse.toLowerCase())
                .as("Response should contain expected text")
                .contains("streaming");
    }
}
