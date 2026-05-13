package io.github.xiaoailazy.coexistree.indexer.llm;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiChatModel;
import io.github.xiaoailazy.coexistree.config.LlmProperties;
import io.github.xiaoailazy.coexistree.shared.entity.DocLlmCallLogEntity;
import io.github.xiaoailazy.coexistree.shared.repository.DocLlmCallLogRepository;
import io.github.xiaoailazy.coexistree.shared.util.LlmCallContext;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Component
public class LlmClient {

    private final LlmProperties llmProperties;
    private final ChatModel chatModel;
    private final DocLlmCallLogRepository llmCallLogRepository;

    public LlmClient(LlmProperties llmProperties,
                     DocLlmCallLogRepository llmCallLogRepository) {
        this.llmProperties = llmProperties;
        this.llmCallLogRepository = llmCallLogRepository;

        Map<String, Object> thinkingDisabled = Map.of("thinking", Map.of("type", "disabled"));

        this.chatModel = OpenAiChatModel.builder()
                .baseUrl(llmProperties.getBaseUrl())
                .apiKey(llmProperties.getApiKey())
                .modelName(llmProperties.getModel())
                .timeout(java.time.Duration.ofMillis(llmProperties.getTimeout()))
                .customParameters(thinkingDisabled)
                .logRequests(false)
                .logResponses(false)
                .build();

        log.info("LlmClient initialized with baseUrl={}, model={}, thinking=disabled",
                llmProperties.getBaseUrl(), llmProperties.getModel());
    }

    public LlmResponse chat(String prompt, String model, Double temperature) {
        return chat(prompt, model, temperature, null);
    }

    public LlmResponse chat(String prompt, String model, Double temperature, String previousResponseId) {
        String resolvedModel = resolveModel(model);
        double resolvedTemp = temperature != null ? temperature : llmProperties.getTemperature();

        log.debug("Starting LLM chat, model={}, promptLength={}", resolvedModel, prompt.length());

        LlmCallContext.LlmCallInfo ctx = LlmCallContext.get().orElse(null);
        long startTime = System.currentTimeMillis();

        try {
            ChatModel modelInstance = buildChatModel(resolvedModel, resolvedTemp);

            ChatRequest request = ChatRequest.builder()
                    .messages(new UserMessage(prompt))
                    .build();

            ChatResponse response = modelInstance.chat(request);
            long elapsed = System.currentTimeMillis() - startTime;

            AiMessage aiMessage = response.aiMessage();
            String content = aiMessage != null ? aiMessage.text() : "";
            if (content == null) content = "";

            LlmResponse.Usage usage = extractUsage(response);

            // Record LLM call log if context is present
            if (ctx != null) {
                recordLlmCall(ctx, resolvedModel, resolvedTemp, usage, elapsed, null);
            }

            log.debug("LLM chat completed, elapsed={}ms, responseLength={}", elapsed, content.length());
            return new LlmResponse(null, content, usage);

        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - startTime;
            log.error("LLM chat failed, elapsed={}ms", elapsed, e);

            if (ctx != null) {
                recordLlmCall(ctx, resolvedModel, resolvedTemp, null, elapsed, e.getMessage());
            }

            return new LlmResponse(null, "Error: " + e.getMessage(), null);
        }
    }

    private void recordLlmCall(LlmCallContext.LlmCallInfo ctx,
                               String model,
                               double temperature,
                               LlmResponse.Usage usage,
                               long elapsedMs,
                               String errorMessage) {
        try {
            DocLlmCallLogEntity entity = new DocLlmCallLogEntity();
            entity.setProcessLogId(ctx.processLogId());
            entity.setModel(model);
            entity.setTemperature(temperature);
            if (usage != null) {
                entity.setInputTokens(usage.inputTokens());
                entity.setOutputTokens(usage.outputTokens());
                entity.setTotalTokens(usage.totalTokens());
                entity.setReasoningTokens(usage.reasoningTokens());
            }
            entity.setElapsedMs(elapsedMs);
            entity.setSuccess(errorMessage == null);
            entity.setErrorMessage(errorMessage);
            entity.setCreatedAt(LocalDateTime.now());
            llmCallLogRepository.save(entity);
            log.debug("LLM call logged: model={}, elapsed={}ms, success={}", model, elapsedMs, errorMessage == null);
        } catch (Exception e) {
            log.warn("Failed to persist LLM call log: {}", e.getMessage());
        }
    }

    private ChatModel buildChatModel(String model, double temperature) {
        Map<String, Object> thinkingDisabled = Map.of("thinking", Map.of("type", "disabled"));
        return OpenAiChatModel.builder()
                .baseUrl(llmProperties.getBaseUrl())
                .apiKey(llmProperties.getApiKey())
                .modelName(model)
                .temperature(temperature)
                .timeout(java.time.Duration.ofMillis(llmProperties.getTimeout()))
                .customParameters(thinkingDisabled)
                .logRequests(false)
                .logResponses(false)
                .build();
    }

    private LlmResponse.Usage extractUsage(ChatResponse response) {
        if (response == null) return null;

        dev.langchain4j.model.output.TokenUsage tokenUsage = response.tokenUsage();
        if (tokenUsage == null) return null;

        return new LlmResponse.Usage(
                tokenUsage.inputTokenCount(),
                tokenUsage.outputTokenCount(),
                tokenUsage.totalTokenCount(),
                0
        );
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

    public record LlmResponse(String responseId, String content, Usage usage) {
        public record Usage(long inputTokens, long outputTokens, long totalTokens, long reasoningTokens) {}
    }
}
