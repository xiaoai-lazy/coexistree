package io.github.xiaoailazy.coexistree.indexer.llm;

import io.github.xiaoailazy.coexistree.indexer.model.TreeSearchResult;
import io.github.xiaoailazy.coexistree.knowledge.model.MergeInstruction;
import io.github.xiaoailazy.coexistree.knowledge.model.SystemTreeStructure;
import io.github.xiaoailazy.coexistree.shared.util.LlmCallContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * 带重试机制的 LLM 服务
 * 当 LLM 返回的数据结构不符合预期时，自动重试并提示 LLM 修正格式
 */
@Slf4j
@Service
public class RetryableLlmService {

    private static final int MAX_RETRIES = 3;
    private static final double TEMPERATURE_INCREMENT = 0.1;

    private final LlmClient llmClient;
    private final LlmResponseParser llmResponseParser;
    private final LlmResponseValidator validator;

    public RetryableLlmService(LlmClient llmClient,
                               LlmResponseParser llmResponseParser,
                               LlmResponseValidator validator) {
        this.llmClient = llmClient;
        this.llmResponseParser = llmResponseParser;
        this.validator = validator;
    }

    /**
     * 执行带重试的 LLM 调用（泛型版本）
     *
     * @param prompt 原始提示词
     * @param model 模型名称
     * @param baseTemperature 基础 temperature
     * @param validator 验证函数
     * @param parser 解析函数
     * @param schemaHint 数据结构提示（用于重试时指导 LLM）
     * @param <T> 返回类型
     * @return 解析后的结果
     * @throws LlmRetryExhaustedException 当所有重试都失败时抛出
     */
    public <T> T executeWithRetry(String prompt,
                                  String model,
                                  Double baseTemperature,
                                  Function<String, LlmResponseValidator.ValidationResult> validator,
                                  Function<String, T> parser,
                                  String schemaHint) {
        String currentPrompt = prompt;
        double currentTemp = baseTemperature != null ? baseTemperature : 0.0;
        LlmResponseValidator.ValidationResult lastValidation = null;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            log.debug("LLM call attempt {}/{} with temp={}", attempt, MAX_RETRIES, currentTemp);

            try {
                // 调用 LLM
                LlmClient.LlmResponse response = llmClient.chat(currentPrompt, model, currentTemp);
                String content = response.content();

                if (content == null || content.trim().isEmpty()) {
                    log.warn("Attempt {}: Empty response from LLM", attempt);
                    lastValidation = LlmResponseValidator.ValidationResult.failure("Empty response", null);
                } else {
                    // 验证结构
                    lastValidation = validator.apply(content);

                    if (lastValidation.valid()) {
                        // 验证通过，尝试解析
                        try {
                            T result = parser.apply(content);
                            if (attempt > 1) {
                                log.info("LLM call succeeded after {} attempts", attempt);
                            }
                            return result;
                        } catch (Exception e) {
                            log.warn("Attempt {}: Parse error despite validation passing: {}", attempt, e.getMessage());
                            lastValidation = LlmResponseValidator.ValidationResult.failure(
                                "Parse error: " + e.getMessage(), null);
                        }
                    } else {
                        log.warn("Attempt {}: Validation failed - {}", attempt, lastValidation.errorMessage());
                    }
                }

                // 如果不是最后一次尝试，构建重试提示
                if (attempt < MAX_RETRIES) {
                    currentPrompt = buildRetryPrompt(prompt, content, lastValidation.errorMessage(), schemaHint);
                    currentTemp = Math.min(currentTemp + TEMPERATURE_INCREMENT, 1.0);
                    log.debug("Retrying with adjusted prompt and temp={}", currentTemp);
                }

            } catch (Exception e) {
                log.error("Attempt {}: LLM call failed with exception: {}", attempt, e.getMessage());
                lastValidation = LlmResponseValidator.ValidationResult.failure(
                    "Exception: " + e.getMessage(), null);

                if (attempt < MAX_RETRIES) {
                    currentTemp = Math.min(currentTemp + TEMPERATURE_INCREMENT, 1.0);
                }
            }
        }

        // 所有重试都失败
        String errorMsg = String.format("LLM call failed after %d attempts. Last error: %s",
            MAX_RETRIES, lastValidation != null ? lastValidation.errorMessage() : "Unknown error");
        log.error(errorMsg);
        throw new LlmRetryExhaustedException(errorMsg, lastValidation);
    }

    /**
     * 树搜索 - 带重试
     */
    public TreeSearchResult treeSearch(String prompt, String model, Double temperature) {
        LlmCallContext.set("TREE_SEARCH", null, null, null);
        try {
            return executeWithRetry(
                prompt,
                model,
                temperature,
                validator::validateTreeSearch,
                llmResponseParser::parseTreeSearch,
                TREE_SEARCH_SCHEMA_HINT
            );
        } finally {
            LlmCallContext.clear();
        }
    }

    /**
     * 基线合并 - 带重试
     */
    public SystemTreeStructure generateSystemTreeStructure(String prompt, String model, Double temperature) {
        LlmCallContext.set("BASELINE_MERGE", null, null, null);
        try {
            return executeWithRetry(
                prompt,
                model,
                temperature,
                validator::validateSystemTreeStructure,
                llmResponseParser::parseSystemTreeStructure,
                SYSTEM_TREE_SCHEMA_HINT
            );
        } finally {
            LlmCallContext.clear();
        }
    }

    /**
     * 变更合并 - 带重试
     */
    public List<MergeInstruction> generateMergeInstructions(String prompt, String model, Double temperature) {
        LlmCallContext.set("CHANGE_MERGE", null, null, null);
        try {
            return executeWithRetry(
                prompt,
                model,
                temperature,
                validator::validateMergeInstructions,
                llmResponseParser::parseMergeInstructions,
                MERGE_INSTRUCTIONS_SCHEMA_HINT
            );
        } finally {
            LlmCallContext.clear();
        }
    }

    /**
     * 带 ResponseId 返回的树搜索 - 带重试
     */
    public TreeSearchResultWithResponseId treeSearchWithResponseId(String prompt, String model, Double temperature) {
        LlmCallContext.set("TREE_SEARCH", null, null, null);
        try {
            return executeWithRetryAndResponseId(
                prompt,
                model,
                temperature,
                validator::validateTreeSearch,
                (content, responseId) -> {
                    TreeSearchResult result = llmResponseParser.parseTreeSearch(content);
                    result.setResponseId(responseId);
                    return new TreeSearchResultWithResponseId(result, responseId);
                },
                TREE_SEARCH_SCHEMA_HINT
            );
        } finally {
            LlmCallContext.clear();
        }
    }

    /**
     * 执行带重试的 LLM 调用（返回包含 ResponseId 的结果）
     */
    private <T> T executeWithRetryAndResponseId(String prompt,
                                                 String model,
                                                 Double baseTemperature,
                                                 Function<String, LlmResponseValidator.ValidationResult> validator,
                                                 BiFunction<String, String, T> parser,
                                                 String schemaHint) {
        String currentPrompt = prompt;
        double currentTemp = baseTemperature != null ? baseTemperature : 0.0;
        LlmResponseValidator.ValidationResult lastValidation = null;
        String lastResponseId = null;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            log.debug("LLM call attempt {}/{} with temp={}", attempt, MAX_RETRIES, currentTemp);

            try {
                // 调用 LLM
                LlmClient.LlmResponse response = llmClient.chat(currentPrompt, model, currentTemp);
                String content = response.content();
                lastResponseId = response.responseId();

                if (content == null || content.trim().isEmpty()) {
                    log.warn("Attempt {}: Empty response from LLM", attempt);
                    lastValidation = LlmResponseValidator.ValidationResult.failure("Empty response", null);
                } else {
                    // 验证结构
                    lastValidation = validator.apply(content);

                    if (lastValidation.valid()) {
                        // 验证通过，尝试解析
                        try {
                            T result = parser.apply(content, lastResponseId);
                            if (attempt > 1) {
                                log.info("LLM call succeeded after {} attempts", attempt);
                            }
                            return result;
                        } catch (Exception e) {
                            log.warn("Attempt {}: Parse error despite validation passing: {}", attempt, e.getMessage());
                            lastValidation = LlmResponseValidator.ValidationResult.failure(
                                "Parse error: " + e.getMessage(), null);
                        }
                    } else {
                        log.warn("Attempt {}: Validation failed - {}", attempt, lastValidation.errorMessage());
                    }
                }

                // 如果不是最后一次尝试，构建重试提示
                if (attempt < MAX_RETRIES) {
                    currentPrompt = buildRetryPrompt(prompt, content, lastValidation.errorMessage(), schemaHint);
                    currentTemp = Math.min(currentTemp + TEMPERATURE_INCREMENT, 1.0);
                    log.debug("Retrying with adjusted prompt and temp={}", currentTemp);
                }

            } catch (Exception e) {
                log.error("Attempt {}: LLM call failed with exception: {}", attempt, e.getMessage());
                lastValidation = LlmResponseValidator.ValidationResult.failure(
                    "Exception: " + e.getMessage(), null);

                if (attempt < MAX_RETRIES) {
                    currentTemp = Math.min(currentTemp + TEMPERATURE_INCREMENT, 1.0);
                }
            }
        }

        // 所有重试都失败
        String errorMsg = String.format("LLM call failed after %d attempts. Last error: %s",
            MAX_RETRIES, lastValidation != null ? lastValidation.errorMessage() : "Unknown error");
        log.error(errorMsg);
        throw new LlmRetryExhaustedException(errorMsg, lastValidation);
    }

    /**
     * 树搜索结果包装类（包含 ResponseId）
     */
    public record TreeSearchResultWithResponseId(TreeSearchResult result, String responseId) {
    }

    /**
     * 构建重试提示词
     */
    private String buildRetryPrompt(String originalPrompt,
                                    String failedResponse,
                                    String errorMessage,
                                    String schemaHint) {
        return String.format("""
            %s

            ---

            IMPORTANT: Your previous response had a format error:
            %s

            Please fix the format and return ONLY a valid JSON response.

            Required JSON Schema:
            %s

            Rules:
            1. Return ONLY the JSON, no markdown code blocks, no explanations
            2. Do not wrap the JSON in ```json or ``` markers
            3. Ensure all required fields are present
            4. Ensure field names match exactly (case-sensitive)
            """,
            originalPrompt,
            errorMessage,
            schemaHint
        );
    }

    // Schema 提示模板
    private static final String TREE_SEARCH_SCHEMA_HINT = """
        {
          "thinking": "your reasoning process as a string",
          "node_list": ["node_id_1", "node_id_2", ...]
        }
        """;

    private static final String SYSTEM_TREE_SCHEMA_HINT = """
        {
          "system_description": "optional system description",
          "structure": [
            {
              "title": "required node title",
              "level": 1,
              "source_node_ids": ["doc_node_id_1", ...],
              "children": [...]
            }
          ]
        }
        """;

    private static final String MERGE_INSTRUCTIONS_SCHEMA_HINT = """
        [
          {
            "operation": "UPDATE|CREATE|MOVE|DELETE",
            "target_node_id": "required for UPDATE/MOVE/DELETE",
            "source_node_id": "required for UPDATE/CREATE",
            "new_parent_node_id": "required for MOVE"
          }
        ]
        """;

    /**
     * 重试耗尽异常
     */
    public static class LlmRetryExhaustedException extends RuntimeException {
        private final LlmResponseValidator.ValidationResult lastValidation;

        public LlmRetryExhaustedException(String message, LlmResponseValidator.ValidationResult lastValidation) {
            super(message);
            this.lastValidation = lastValidation;
        }

        public LlmResponseValidator.ValidationResult getLastValidation() {
            return lastValidation;
        }
    }
}
