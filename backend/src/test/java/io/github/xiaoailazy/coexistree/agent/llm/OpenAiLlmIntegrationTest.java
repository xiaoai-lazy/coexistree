package io.github.xiaoailazy.coexistree.agent.llm;

import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import io.github.xiaoailazy.coexistree.config.LlmProperties;
import io.reactivex.rxjava3.subscribers.TestSubscriber;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for OpenAiLlm with real LLM calls.
 *
 * Set LLM_TEST_ENABLED=true to enable these tests.
 * By default they are skipped to avoid calling external APIs.
 */
@Slf4j
@SpringBootTest
@ActiveProfiles("test")
class OpenAiLlmIntegrationTest {

    @Autowired
    private LlmProperties llmProperties;

    private OpenAiLlm llm;

    private static String extractText(LlmResponse response) {
        return response.content()
                .map(Content::text)
                .orElse("");
    }

    @BeforeEach
    void setUp() {
        String enabled = System.getenv("LLM_TEST_ENABLED");
        if (enabled == null) {
            enabled = System.getProperty("LLM_TEST_ENABLED");
        }
        boolean shouldRun = "true".equalsIgnoreCase(enabled);
        log.info("LLM_TEST_ENABLED={}, should run tests={}", enabled, shouldRun);
        Assumptions.assumeTrue(shouldRun, "Skipping LLM tests. Set LLM_TEST_ENABLED=true to enable.");

        OpenAIClient client = OpenAIOkHttpClient.builder()
                .apiKey(llmProperties.getApiKey())
                .baseUrl(llmProperties.getBaseUrl())
                .build();

        this.llm = new OpenAiLlm(llmProperties.getModel(), client);
    }

    @Test
    void shouldGenerateStreamingResponse() {
        log.info("Testing OpenAiLlm streaming, model={}", llm.model());

        LlmRequest request = LlmRequest.builder()
                .contents(List.of(Content.fromParts(Part.fromText("Say 'streaming works' and nothing else"))))
                .build();

        TestSubscriber<LlmResponse> subscriber = llm.generateContent(request, true).test();
        subscriber.awaitDone(120, TimeUnit.SECONDS);

        subscriber.assertComplete();
        subscriber.assertNoErrors();

        List<LlmResponse> responses = subscriber.values();
        assertThat(responses).as("Should receive at least one streaming chunk").isNotEmpty();

        StringBuilder fullText = new StringBuilder();
        for (LlmResponse response : responses) {
            String text = extractText(response);
            if (!text.isEmpty()) {
                fullText.append(text);
            }
        }
        log.info("Full streaming response: {}", fullText);
        assertThat(fullText.toString()).isNotBlank();
    }

    @Test
    void shouldGenerateBlockingResponse() {
        log.info("Testing OpenAiLlm blocking, model={}", llm.model());

        LlmRequest request = LlmRequest.builder()
                .contents(List.of(Content.fromParts(Part.fromText("Say 'blocking works' and nothing else"))))
                .build();

        TestSubscriber<LlmResponse> subscriber = llm.generateContent(request, false).test();
        subscriber.awaitDone(120, TimeUnit.SECONDS);

        subscriber.assertComplete();
        subscriber.assertNoErrors();

        List<LlmResponse> responses = subscriber.values();
        assertThat(responses).as("Should receive exactly one response for blocking call").hasSize(1);

        String text = extractText(responses.get(0));
        log.info("Blocking response: {}", text);
        assertThat(text).isNotBlank();
    }

    @Test
    void shouldHandleConnectionLifecycle() {
        log.info("Testing OpenAiLlm connection lifecycle, model={}", llm.model());

        LlmRequest request = LlmRequest.builder()
                .contents(List.of(Content.fromParts(Part.fromText("Say 'connection works' and nothing else"))))
                .build();

        var connection = llm.connect(request);
        assertThat(connection).isNotNull();

        // Send additional content
        connection.sendContent(Content.fromParts(Part.fromText("Follow up"))).blockingAwait();

        // Receive streaming response via connection
        TestSubscriber<LlmResponse> subscriber = connection.receive().test();
        subscriber.awaitDone(120, TimeUnit.SECONDS);
        subscriber.assertComplete();
        subscriber.assertNoErrors();

        assertThat(subscriber.values()).isNotEmpty();

        // Close connection
        connection.close();
    }

    @Test
    void shouldHandleConnectionHistoryReplacement() {
        log.info("Testing OpenAiLlm history replacement, model={}", llm.model());

        LlmRequest request = LlmRequest.builder().build();
        var connection = llm.connect(request);

        // Send initial history
        List<Content> history = List.of(
                Content.fromParts(Part.fromText("You are a helpful assistant."))
        );
        connection.sendHistory(history).blockingAwait();

        // Send new content
        connection.sendContent(Content.fromParts(Part.fromText("Say 'history works'"))).blockingAwait();

        // Receive response
        TestSubscriber<LlmResponse> subscriber = connection.receive().test();
        subscriber.awaitDone(120, TimeUnit.SECONDS);
        subscriber.assertComplete();
        subscriber.assertNoErrors();
        assertThat(subscriber.values()).isNotEmpty();

        connection.close();
    }

    @Test
    void shouldCloseConnectionWithError() {
        LlmRequest request = LlmRequest.builder().build();
        var connection = llm.connect(request);

        // Should not throw
        connection.close(new RuntimeException("Test error"));
    }
}
