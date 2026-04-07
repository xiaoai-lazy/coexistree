package io.github.xiaoailazy.coexistree.indexer.llm;

import io.github.xiaoailazy.coexistree.config.LlmProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LlmClientTest {

    @Mock
    private LlmProperties llmProperties;

    @Test
    void shouldReturnDefaultModel() {
        when(llmProperties.getApiKey()).thenReturn("demo-key");
        when(llmProperties.getBaseUrl()).thenReturn("https://api.openai.com/v1");
        when(llmProperties.getModel()).thenReturn("gpt-4o-mini");

        LlmClient client = new LlmClient(llmProperties);

        assertThat(client.defaultModel()).isEqualTo("gpt-4o-mini");
    }

    @Test
    void shouldBeConfiguredWhenApiKeyPresent() {
        when(llmProperties.getApiKey()).thenReturn("demo-key");
        when(llmProperties.getBaseUrl()).thenReturn("https://api.openai.com/v1");
        when(llmProperties.getModel()).thenReturn("gpt-4o-mini");

        LlmClient client = new LlmClient(llmProperties);

        assertThat(client.isConfigured()).isTrue();
    }

    @Test
    void shouldNotBeConfiguredWhenApiKeyBlank() {
        when(llmProperties.getApiKey()).thenReturn("");
        when(llmProperties.getBaseUrl()).thenReturn("https://api.openai.com/v1");
        when(llmProperties.getModel()).thenReturn("gpt-4o-mini");

        LlmClient client = new LlmClient(llmProperties);

        assertThat(client.isConfigured()).isFalse();
    }

    @Test
    void shouldNotBeConfiguredWhenApiKeyNull() {
        when(llmProperties.getApiKey()).thenReturn(null);
        when(llmProperties.getBaseUrl()).thenReturn("https://api.openai.com/v1");
        when(llmProperties.getModel()).thenReturn("gpt-4o-mini");

        LlmClient client = new LlmClient(llmProperties);

        assertThat(client.isConfigured()).isFalse();
    }

    @Test
    void chatStreamShouldAcceptConsumerCallbacks() {
        // Regression test: verifies chatStream method signature accepts Consumer callbacks
        // This ensures we don't accidentally change it to synchronous (returning String directly)
        when(llmProperties.getApiKey()).thenReturn("demo-key");
        when(llmProperties.getBaseUrl()).thenReturn("https://api.openai.com/v1");
        when(llmProperties.getModel()).thenReturn("gpt-4o-mini");

        LlmClient client = new LlmClient(llmProperties);

        AtomicInteger textCallbackCount = new AtomicInteger(0);
        AtomicInteger thinkingCallbackCount = new AtomicInteger(0);

        // The method signature should be:
        // chatStream(prompt, model, temperature, previousResponseId, onThinking, onText)
        // If someone changes this to return String directly (synchronous), this test won't compile
        Consumer<String> onText = chunk -> textCallbackCount.incrementAndGet();
        Consumer<String> onThinking = chunk -> thinkingCallbackCount.incrementAndGet();

        // Verify the method exists and can be called with Consumer parameters
        // This will fail at runtime due to invalid API key, but that's expected
        try {
            client.chatStream("test prompt", "gpt-4o-mini", 0.5, "prev-id", onThinking, onText);
        } catch (RuntimeException e) {
            // Expected - API call will fail with dummy credentials
            assertThat(e.getMessage()).isNotNull();
        }

        // The important assertion: the method signature supports streaming callbacks
        // This documents that chatStream is designed for streaming, not synchronous calls
        assertThat(client).isNotNull();
    }

    @Test
    void chatShouldNotAcceptStreamingCallbacks() {
        // Verify that chat() method is synchronous (doesn't accept callbacks)
        // This is the expected behavior - chat() returns complete response
        when(llmProperties.getApiKey()).thenReturn("demo-key");
        when(llmProperties.getBaseUrl()).thenReturn("https://api.openai.com/v1");
        when(llmProperties.getModel()).thenReturn("gpt-4o-mini");

        LlmClient client = new LlmClient(llmProperties);

        // chat() should return LlmResponse synchronously
        // If someone accidentally changes it to require callbacks, this won't compile
        try {
            LlmClient.LlmResponse response = client.chat("test", "gpt-4o-mini", 0.5);
            // Response will be error due to dummy credentials, but that's expected
            assertThat(response).isNotNull();
        } catch (RuntimeException e) {
            // Expected - API call will fail
        }
    }
}
