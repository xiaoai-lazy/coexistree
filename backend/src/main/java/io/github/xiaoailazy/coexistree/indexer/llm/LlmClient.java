package io.github.xiaoailazy.coexistree.indexer.llm;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.http.StreamResponse;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseOutputItem;
import com.openai.models.responses.ResponseOutputMessage;
import com.openai.models.responses.ResponseOutputText;
import com.openai.models.responses.ResponseStreamEvent;
import io.github.xiaoailazy.coexistree.config.LlmProperties;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Consumer;

@Slf4j
@Component
public class LlmClient {

    private final LlmProperties llmProperties;
    private final OpenAIClient openAIClient;

    public LlmClient(LlmProperties llmProperties) {
        this.llmProperties = llmProperties;
        String apiKey = llmProperties.getApiKey();
        String resolvedApiKey = (apiKey != null && !apiKey.isBlank()) ? apiKey : "dummy-key";
        this.openAIClient = OpenAIOkHttpClient.builder()
                .apiKey(resolvedApiKey)
                .baseUrl(llmProperties.getBaseUrl())
                .build();
        log.info("LlmClient initialized with baseUrl={}, model={}",
                llmProperties.getBaseUrl(), llmProperties.getModel());
    }

    public LlmResponse chat(String prompt, String model, Double temperature) {
        return chat(prompt, model, temperature, null);
    }

    public LlmResponse chat(String prompt, String model, Double temperature, String previousResponseId) {
        String resolvedModel = resolveModel(model);
        double resolvedTemp = temperature != null ? temperature : llmProperties.getTemperature();

        log.debug("Starting LLM chat, model={}, promptLength={}", resolvedModel, prompt.length());

        try {
            ResponseCreateParams.Builder paramsBuilder = ResponseCreateParams.builder()
                    .model(resolvedModel)
                    .input(prompt)
                    .temperature(resolvedTemp);

            if (previousResponseId != null && !previousResponseId.isBlank()) {
                paramsBuilder.previousResponseId(previousResponseId);
            }

            ResponseCreateParams params = paramsBuilder.build();

            long startTime = System.currentTimeMillis();
            Response response = openAIClient.responses().create(params);
            long elapsed = System.currentTimeMillis() - startTime;

            String content = response.output().stream()
                    .filter(ResponseOutputItem::isMessage)
                    .flatMap(item -> item.message().map(List::of).orElseGet(List::of).stream())
                    .flatMap(msg -> msg.content().stream())
                    .filter(ResponseOutputMessage.Content::isOutputText)
                    .flatMap(contentItem -> contentItem.outputText().map(List::of).orElseGet(List::of).stream())
                    .map(ResponseOutputText::text)
                    .findFirst()
                    .orElse("");

            log.debug("LLM chat completed, elapsed={}ms, responseLength={}", elapsed, content.length());
            return new LlmResponse(response.id(), content);

        } catch (Exception e) {
            log.error("LLM chat failed", e);
            return new LlmResponse(null, "Error: " + e.getMessage());
        }
    }

    public String chatStream(String prompt, String model, Double temperature,
                             String previousResponseId,
                             Consumer<String> onThinking, Consumer<String> onText) {
        String resolvedModel = resolveModel(model);
        double resolvedTemp = temperature != null ? temperature : llmProperties.getTemperature();

        log.debug("Starting LLM stream, model={}, promptLength={}", resolvedModel, prompt.length());

        StringBuilder textBuilder = new StringBuilder();
        String[] responseIdHolder = new String[1];

        try {
            ResponseCreateParams.Builder paramsBuilder = ResponseCreateParams.builder()
                    .model(resolvedModel)
                    .input(prompt)
                    .temperature(resolvedTemp);

            if (previousResponseId != null && !previousResponseId.isBlank()) {
                paramsBuilder.previousResponseId(previousResponseId);
            }

            ResponseCreateParams params = paramsBuilder.build();

            long startTime = System.currentTimeMillis();

            try (StreamResponse<ResponseStreamEvent> stream = openAIClient.responses().createStreaming(params)) {
                stream.stream().forEach(event -> {
                    // Process output text delta events
                    if (event.isOutputTextDelta()) {
                        String text = event.asOutputTextDelta().delta();
                        textBuilder.append(text);
                        if (onText != null) {
                            onText.accept(text);
                        }
                    }
                    // Process response completed event to extract responseId
                    else if (event.isCompleted()) {
                        Response completedResponse = event.asCompleted().response();
                        responseIdHolder[0] = completedResponse.id();
                        log.debug("Extracted responseId from stream completion: {}", responseIdHolder[0]);
                    }
                });
            }

            long elapsed = System.currentTimeMillis() - startTime;
            log.debug("LLM stream completed, elapsed={}ms, textLength={}, responseId={}",
                    elapsed, textBuilder.length(), responseIdHolder[0]);

            return responseIdHolder[0];

        } catch (Exception e) {
            log.error("LLM stream failed", e);
            throw new RuntimeException("LLM stream failed", e);
        }
    }

    public String defaultModel() {
        return llmProperties.getModel();
    }

    public boolean isConfigured() {
        return llmProperties.getApiKey() != null && !llmProperties.getApiKey().isBlank();
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down LlmClient");
    }

    private String resolveModel(String model) {
        return (model != null && !model.isBlank()) ? model : llmProperties.getModel();
    }

    public record LlmResponse(String responseId, String content) {}
}
