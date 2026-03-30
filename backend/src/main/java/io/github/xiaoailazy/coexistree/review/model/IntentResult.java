package io.github.xiaoailazy.coexistree.review.model;

import io.github.xiaoailazy.coexistree.review.enums.ConfidenceLevel;
import io.github.xiaoailazy.coexistree.review.enums.IntentType;

/**
 * 意图识别结果
 */
public record IntentResult(
        /**
         * 识别出的意图类型
         */
        IntentType intent,

        /**
         * 置信度级别
         */
        ConfidenceLevel confidence,

        /**
         * 判断理由
         */
        String reason,

        /**
         * 当置信度为 MEDIUM 或 LOW 时的建议操作
         */
        String suggestedAction
) {

    /**
     * 是否需要用户澄清
     */
    public boolean needsClarification() {
        return confidence == ConfidenceLevel.MEDIUM ||
               (confidence == ConfidenceLevel.LOW && intent == IntentType.REQUIREMENT_EVAL);
    }

    /**
     * 是否可以继续执行（高置信度或明确的需求评估）
     */
    public boolean canProceed() {
        return confidence == ConfidenceLevel.HIGH;
    }

    /**
     * 创建高置信度的问答意图结果
     */
    public static IntentResult questionHigh(String reason) {
        return new IntentResult(IntentType.QUESTION, ConfidenceLevel.HIGH, reason, null);
    }

    /**
     * 创建高置信度的需求评估意图结果
     */
    public static IntentResult evalHigh(String reason) {
        return new IntentResult(IntentType.REQUIREMENT_EVAL, ConfidenceLevel.HIGH, reason, null);
    }

    /**
     * 创建中置信度的结果（需要澄清）
     */
    public static IntentResult medium(IntentType intent, String reason, String suggestedAction) {
        return new IntentResult(intent, ConfidenceLevel.MEDIUM, reason, suggestedAction);
    }

    /**
     * 创建低置信度的结果
     */
    public static IntentResult low(IntentType intent, String reason, String suggestedAction) {
        return new IntentResult(intent, ConfidenceLevel.LOW, reason, suggestedAction);
    }

    /**
     * 创建需要澄清的结果
     */
    public static IntentResult clarification(String reason, String suggestedAction) {
        return new IntentResult(IntentType.CLARIFICATION, ConfidenceLevel.LOW, reason, suggestedAction);
    }
}
