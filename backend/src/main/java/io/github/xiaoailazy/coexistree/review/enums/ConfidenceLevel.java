package io.github.xiaoailazy.coexistree.review.enums;

/**
 * 意图识别置信度级别
 */
public enum ConfidenceLevel {
    /**
     * 高置信度 - 直接执行
     */
    HIGH,

    /**
     * 中置信度 - 返回澄清选项让用户确认
     */
    MEDIUM,

    /**
     * 低置信度 - 返回提示信息
     */
    LOW
}
