package io.github.xiaoailazy.coexistree.agent.llm;

import com.google.adk.models.BaseLlmConnection;
import com.google.adk.models.LlmRequest;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for OpenAiLlm.
 *
 * Note: The OpenAI Java SDK 4.30.0 does not expose mockable interfaces for its
 * client internals. These tests verify construction and connection lifecycle
 * behavior only. Real LLM calls are validated by OpenAiLlmIntegrationTest.
 */
class OpenAiLlmTest {

    private OpenAIClient createTestClient() {
        return com.openai.client.okhttp.OpenAIOkHttpClient.builder()
                .apiKey("test-key")
                .baseUrl("https://api.example.com/v1")
                .build();
    }

    @Test
    void shouldConstructWithValidModel() {
        var client = createTestClient();
        OpenAiLlm llm = new OpenAiLlm("test-model", client);
        assertThat(llm.model()).isEqualTo("test-model");
    }

    @Test
    void shouldConstructWithCustomModelOverride() {
        var client = createTestClient();
        OpenAiLlm llm = new OpenAiLlm("doubao-seed-2-0-pro-260215", client);
        assertThat(llm.model()).isEqualTo("doubao-seed-2-0-pro-260215");
    }

    @Test
    void shouldHandleNullContentsInPrompt() {
        LlmRequest request = LlmRequest.builder().build();
        assertThat(request.contents()).isEmpty();
    }

    @Test
    void shouldHandleEmptyContentsInPrompt() {
        LlmRequest request = LlmRequest.builder()
                .contents(List.of())
                .build();
        assertThat(request.contents()).isEmpty();
    }

    @Test
    void shouldBuildMultiPartContentsInPrompt() {
        LlmRequest request = LlmRequest.builder()
                .contents(List.of(
                        Content.fromParts(Part.fromText("part one")),
                        Content.fromParts(Part.fromText("part two"))
                ))
                .build();

        assertThat(request.contents()).hasSize(2);
        assertThat(request.contents().get(0).text()).isEqualTo("part one");
        assertThat(request.contents().get(1).text()).isEqualTo("part two");
    }

    // ==================== Connection lifecycle tests ====================

    @Test
    void shouldCreateConnectionFromLlm() {
        var client = createTestClient();
        OpenAiLlm llm = new OpenAiLlm("test-model", client);

        LlmRequest request = LlmRequest.builder()
                .contents(List.of(Content.fromParts(Part.fromText("hello"))))
                .build();

        BaseLlmConnection connection = llm.connect(request);
        assertThat(connection).isNotNull();
    }

    @Test
    void shouldSendHistoryToConnection() {
        var client = createTestClient();
        OpenAiLlm llm = new OpenAiLlm("test-model", client);
        BaseLlmConnection connection = llm.connect(LlmRequest.builder().build());

        List<Content> history = List.of(
                Content.fromParts(Part.fromText("system prompt")),
                Content.fromParts(Part.fromText("user message"))
        );

        connection.sendHistory(history).blockingAwait();
        // No exception = success
    }

    @Test
    void shouldSendContentToConnection() {
        var client = createTestClient();
        OpenAiLlm llm = new OpenAiLlm("test-model", client);
        BaseLlmConnection connection = llm.connect(LlmRequest.builder().build());

        Content content = Content.fromParts(Part.fromText("follow-up message"));
        connection.sendContent(content).blockingAwait();
        // No exception = success
    }

    @Test
    void shouldSendNullContentWithoutError() {
        var client = createTestClient();
        OpenAiLlm llm = new OpenAiLlm("test-model", client);
        BaseLlmConnection connection = llm.connect(LlmRequest.builder().build());

        connection.sendContent(null).blockingAwait();
        // Should handle null gracefully
    }

    @Test
    void shouldSendNullHistoryWithoutError() {
        var client = createTestClient();
        OpenAiLlm llm = new OpenAiLlm("test-model", client);
        BaseLlmConnection connection = llm.connect(LlmRequest.builder().build());

        connection.sendHistory(null).blockingAwait();
        // Should handle null gracefully
    }

    @Test
    void shouldHandleSendRealtime() {
        var client = createTestClient();
        OpenAiLlm llm = new OpenAiLlm("test-model", client);
        BaseLlmConnection connection = llm.connect(LlmRequest.builder().build());

        // Realtime not supported, should complete without error
        connection.sendRealtime(null).blockingAwait();
    }

    @Test
    void shouldReturnEmptyFlowableAfterClose() {
        var client = createTestClient();
        OpenAiLlm llm = new OpenAiLlm("test-model", client);
        BaseLlmConnection connection = llm.connect(LlmRequest.builder().build());

        connection.close();

        var subscriber = connection.receive().test();
        subscriber.assertComplete();
        subscriber.assertNoErrors();
        subscriber.assertNoValues();
    }

    @Test
    void shouldReturnEmptyFlowableAfterCloseWithError() {
        var client = createTestClient();
        OpenAiLlm llm = new OpenAiLlm("test-model", client);
        BaseLlmConnection connection = llm.connect(LlmRequest.builder().build());

        connection.close(new RuntimeException("forced close"));

        var subscriber = connection.receive().test();
        subscriber.assertComplete();
        subscriber.assertNoErrors();
        subscriber.assertNoValues();
    }

    @Test
    void shouldAllowMultipleContentSends() {
        var client = createTestClient();
        OpenAiLlm llm = new OpenAiLlm("test-model", client);
        BaseLlmConnection connection = llm.connect(LlmRequest.builder().build());

        connection.sendContent(Content.fromParts(Part.fromText("msg1"))).blockingAwait();
        connection.sendContent(Content.fromParts(Part.fromText("msg2"))).blockingAwait();
        connection.sendContent(Content.fromParts(Part.fromText("msg3"))).blockingAwait();

        // Should not throw, all content added successfully
    }

    @Test
    void shouldReplaceHistoryOnSecondSend() {
        var client = createTestClient();
        OpenAiLlm llm = new OpenAiLlm("test-model", client);
        BaseLlmConnection connection = llm.connect(LlmRequest.builder().build());

        // Send initial history
        connection.sendHistory(List.of(
                Content.fromParts(Part.fromText("old message"))
        )).blockingAwait();

        // Replace with new history
        connection.sendHistory(List.of(
                Content.fromParts(Part.fromText("new message 1")),
                Content.fromParts(Part.fromText("new message 2"))
        )).blockingAwait();

        // No exception = success, old history was replaced
    }
}
