package io.github.xiaoailazy.coexistree.review.enums;

/**
 * 用户意图类型
 */
public enum IntentType {
    /**
     * 询问系统现有功能、实现细节、使用方法等
     */
    QUESTION,

    /**
     * 评估新需求/变更需求的影响、冲突、可行性等
     */
    REQUIREMENT_EVAL,

    /**
     * 需要澄清或无法确定意图
     */
    CLARIFICATION
}
