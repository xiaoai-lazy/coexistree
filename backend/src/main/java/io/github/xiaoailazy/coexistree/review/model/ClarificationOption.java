package io.github.xiaoailazy.coexistree.review.model;

import io.github.xiaoailazy.coexistree.review.enums.IntentType;

/**
 * 意图澄清选项
 */
public record ClarificationOption(
        /**
         * 选项标签
         */
        String label,

        /**
         * 选项描述
         */
        String description,

        /**
         * 选项对应的意图
         */
        IntentType intentValue,

        /**
         * 选项标识符（A, B, C...）
         */
        String marker
) {

    /**
     * 创建标准澄清选项
     */
    public static ClarificationOption question(String marker) {
        return new ClarificationOption(
                "问答",
                "了解系统现有功能",
                IntentType.QUESTION,
                marker
        );
    }

    /**
     * 创建需求评估澄清选项
     */
    public static ClarificationOption evaluation(String marker) {
        return new ClarificationOption(
                "需求评估",
                "评估新增/变更需求的影响",
                IntentType.REQUIREMENT_EVAL,
                marker
        );
    }
}
