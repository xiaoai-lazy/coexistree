package io.github.xiaoailazy.coexistree.review.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.xiaoailazy.coexistree.review.enums.ConfidenceLevel;
import io.github.xiaoailazy.coexistree.review.enums.IntentType;
import io.github.xiaoailazy.coexistree.review.model.IntentResult;
import io.github.xiaoailazy.coexistree.indexer.llm.LlmClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 意图分类器 - 使用 LLM 判断用户意图
 */
@Slf4j
@Component
public class IntentClassifier {

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    // 评估关键词集合
    private static final Set<String> EVAL_KEYWORDS = Set.of(
            "评估", "分析", "影响", "冲突", "能不能做", "可行性", "建议", "是否合理",
            "evaluate", "assess", "analyze", "impact", "conflict", "feasible", "suggestion",
            "风险", "问题", "依赖", "成本", "工作量", "复杂度"
    );

    public IntentClassifier(LlmClient llmClient, ObjectMapper objectMapper) {
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
    }

    /**
     * 分类用户意图
     *
     * @param question     用户问题
     * @param hasDocument  是否已上传文档
     * @return 意图识别结果
     */
    public IntentResult classify(String question, boolean hasDocument) {
        // 首先进行快速关键词检测
        IntentResult quickResult = quickClassify(question, hasDocument);
        if (quickResult != null && quickResult.canProceed()) {
            log.debug("快速分类成功: intent={}, confidence={}", quickResult.intent(), quickResult.confidence());
            return quickResult;
        }

        // 使用 LLM 进行更精确的分类
        return llmClassify(question, hasDocument);
    }

    /**
     * 快速分类 - 基于规则的关键词检测
     */
    private IntentResult quickClassify(String question, boolean hasDocument) {
        String lowerQuestion = question.toLowerCase();
        boolean hasEvalKeyword = EVAL_KEYWORDS.stream()
                .anyMatch(keyword -> lowerQuestion.contains(keyword.toLowerCase()));

        // 提到需求文档/PRD/原型，且含评估关键词 → REQUIREMENT_EVAL HIGH
        if ((lowerQuestion.contains("需求") || lowerQuestion.contains("prd") ||
             lowerQuestion.contains("文档") || lowerQuestion.contains("原型")) && hasEvalKeyword) {
            return IntentResult.evalHigh("问题明确提到需求文档且包含评估关键词");
        }

        // 有文档且含评估关键词 → REQUIREMENT_EVAL HIGH
        if (hasDocument && hasEvalKeyword) {
            return IntentResult.evalHigh("已上传文档且问题包含评估关键词");
        }

        // 询问现有功能的关键词 → QUESTION HIGH
        if (lowerQuestion.contains("怎么") || lowerQuestion.contains("如何") ||
            lowerQuestion.contains("什么") || lowerQuestion.contains("吗？") ||
            lowerQuestion.contains("介绍一下") || lowerQuestion.contains("说明")) {
            return IntentResult.questionHigh("问题格式符合问答特征");
        }

        // 无法快速确定，返回 null 让 LLM 处理
        return null;
    }

    /**
     * 使用 LLM 进行意图分类
     */
    private IntentResult llmClassify(String question, boolean hasDocument) {
        String prompt = buildPrompt(question, hasDocument);

        try {
            LlmClient.LlmResponse response = llmClient.chat(
                    prompt,
                    null,  // 使用默认模型
                    0.3    // 低温度以获得更确定的结果
            );

            return parseResult(response.content(), question, hasDocument);
        } catch (Exception e) {
            log.error("LLM 意图分类失败", e);
            // 失败时默认返回问答意图
            return IntentResult.questionHigh("LLM 分类失败，默认使用问答模式");
        }
    }

    /**
     * 构建分类 Prompt
     */
    private String buildPrompt(String question, boolean hasDocument) {
        return """
                分析以下用户问题，判断意图：

                问题：%s
                是否有上传文档：%s

                可选意图：
                1. QUESTION - 询问系统现有功能、实现细节、使用方法等
                2. REQUIREMENT_EVAL - 评估新需求/变更需求的影响、冲突、可行性等
                3. CLARIFICATION - 无法确定，需要澄清

                评估关键词参考：评估、分析、影响、冲突、能不能做、可行性、建议、是否合理、风险、依赖

                输出格式（JSON）：
                {
                  "intent": "QUESTION|REQUIREMENT_EVAL|CLARIFICATION",
                  "confidence": "HIGH|MEDIUM|LOW",
                  "reason": "判断理由",
                  "suggestedAction": "当置信度为MEDIUM或LOW时的建议操作"
                }

                判断规则：
                - 提到需求文档/PRD/原型，且含评估关键词 → REQUIREMENT_EVAL HIGH
                - 有文档且含评估关键词 → REQUIREMENT_EVAL HIGH
                - 有文档但不含评估关键词 → QUESTION MEDIUM（让用户确认）
                - 无文档但含评估关键词 → REQUIREMENT_EVAL LOW（提示上传文档）
                - 询问现有功能 → QUESTION HIGH
                - 模糊或混合意图 → CLARIFICATION MEDIUM

                请只输出 JSON，不要其他内容。
                """.formatted(question, hasDocument ? "是" : "否");
    }

    /**
     * 解析 LLM 响应
     */
    private IntentResult parseResult(String content, String question, boolean hasDocument) {
        try {
            // 提取 JSON 部分
            String json = extractJson(content);
            JsonNode node = objectMapper.readTree(json);

            IntentType intent = IntentType.valueOf(node.get("intent").asText());
            ConfidenceLevel confidence = ConfidenceLevel.valueOf(node.get("confidence").asText());
            String reason = node.has("reason") ? node.get("reason").asText() : "";
            String suggestedAction = node.has("suggestedAction") ?
                    node.get("suggestedAction").asText() : null;

            // 后处理逻辑修正
            return postProcess(intent, confidence, reason, suggestedAction, hasDocument);

        } catch (Exception e) {
            log.error("解析 LLM 响应失败: {}", content, e);
            // 解析失败时，基于关键词做简单判断
            return fallbackClassify(question, hasDocument);
        }
    }

    /**
     * 从响应中提取 JSON
     */
    private String extractJson(String content) {
        String trimmed = content.trim();

        // 尝试直接解析
        if (trimmed.startsWith("{")) {
            return trimmed;
        }

        // 提取 ```json 代码块
        int start = trimmed.indexOf("```json");
        if (start != -1) {
            int end = trimmed.indexOf("```", start + 7);
            if (end != -1) {
                return trimmed.substring(start + 7, end).trim();
            }
        }

        // 提取 ``` 代码块
        start = trimmed.indexOf("```");
        if (start != -1) {
            int end = trimmed.indexOf("```", start + 3);
            if (end != -1) {
                return trimmed.substring(start + 3, end).trim();
            }
        }

        // 查找第一个 { 和最后一个 }
        start = trimmed.indexOf("{");
        int end = trimmed.lastIndexOf("}");
        if (start != -1 && end != -1 && end > start) {
            return trimmed.substring(start, end + 1);
        }

        return trimmed;
    }

    /**
     * 后处理逻辑 - 修正边界情况
     */
    private IntentResult postProcess(IntentType intent, ConfidenceLevel confidence,
                                     String reason, String suggestedAction, boolean hasDocument) {
        // 如果意图是需求评估但没有文档且置信度低，提示上传
        if (intent == IntentType.REQUIREMENT_EVAL && !hasDocument && confidence == ConfidenceLevel.LOW) {
            return IntentResult.low(
                    IntentType.REQUIREMENT_EVAL,
                    "问题似乎想评估需求，但未上传文档",
                    "请上传需求文档后进行评估"
            );
        }

        // 如果有文档但置信度中等，询问用户确认
        if (hasDocument && confidence == ConfidenceLevel.MEDIUM) {
            return IntentResult.medium(
                    intent,
                    "已上传文档但意图不够明确",
                    "您想基于文档进行问答还是需求评估？"
            );
        }

        return new IntentResult(intent, confidence, reason, suggestedAction);
    }

    /**
     * 兜底分类 - 当 LLM 解析失败时使用
     */
    private IntentResult fallbackClassify(String question, boolean hasDocument) {
        String lowerQuestion = question.toLowerCase();
        boolean hasEvalKeyword = EVAL_KEYWORDS.stream()
                .anyMatch(kw -> lowerQuestion.contains(kw.toLowerCase()));

        if (hasEvalKeyword) {
            if (hasDocument) {
                return IntentResult.evalHigh("兜底分类：包含评估关键词且有文档");
            } else {
                return IntentResult.low(
                        IntentType.REQUIREMENT_EVAL,
                        "包含评估关键词但没有文档",
                        "请上传需求文档后进行评估"
                );
            }
        }

        return IntentResult.questionHigh("兜底分类：默认问答模式");
    }
}
