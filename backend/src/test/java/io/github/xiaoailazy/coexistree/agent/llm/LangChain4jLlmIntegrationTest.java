package io.github.xiaoailazy.coexistree.agent.llm;

import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import com.google.adk.models.langchain4j.LangChain4j;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
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
 * Integration tests for LangChain4j LLM adapter with real LLM calls.
 *
 * Set LLM_TEST_ENABLED=true to enable these tests.
 * By default they are skipped to avoid calling external APIs.
 */
@Slf4j
@SpringBootTest
@ActiveProfiles("test")
class LangChain4jLlmIntegrationTest {

    @Autowired
    private LlmProperties llmProperties;

    private LangChain4j llm;

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

        String model = llmProperties.getModel();
        String baseUrl = llmProperties.getBaseUrl();
        String apiKey = llmProperties.getApiKey();

        StreamingChatModel streamingModel = OpenAiStreamingChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(model)
                .build();

        ChatModel chatModel = OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(model)
                .build();

        this.llm = LangChain4j.builder()
                .chatModel(chatModel)
                .streamingChatModel(streamingModel)
                .modelName(model)
                .build();
    }

    @Test
    void shouldGenerateStreamingResponse() {
        log.info("Testing LangChain4j streaming, model={}", llm.model());

        LlmRequest request = LlmRequest.builder()
                .contents(List.of(Content.fromParts(Part.fromText("Say 'streaming works' and nothing else"))))
                .build();

        TestSubscriber<LlmResponse> subscriber = llm.generateContent(request, true).test();
        subscriber.awaitDone(10, TimeUnit.SECONDS);

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
        log.info("Testing LangChain4j blocking, model={}", llm.model());

        LlmRequest request = LlmRequest.builder()
                .contents(List.of(Content.fromParts(Part.fromText("Say 'blocking works' and nothing else"))))
                .build();

        TestSubscriber<LlmResponse> subscriber = llm.generateContent(request, false).test();
        subscriber.awaitDone(10, TimeUnit.SECONDS);

        subscriber.assertComplete();
        subscriber.assertNoErrors();

        List<LlmResponse> responses = subscriber.values();
        assertThat(responses).as("Should receive exactly one response for blocking call").hasSize(1);

        String text = extractText(responses.get(0));
        log.info("Blocking response: {}", text);
        assertThat(text).isNotBlank();
    }

    // Connection lifecycle tests removed: LangChain4j OpenAI adapter does not
    // support live connections (llm.connect throws UnsupportedOperationException).
}
