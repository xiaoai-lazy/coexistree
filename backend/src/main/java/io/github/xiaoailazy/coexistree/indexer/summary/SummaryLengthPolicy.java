package io.github.xiaoailazy.coexistree.indexer.summary;

/**
 * 摘要长度策略配置
 * 根据节点层级动态调整摘要长度
 */
public class SummaryLengthPolicy {

    // 不同层级的节点推荐摘要长度
    private static final SummaryLengthConfig[] SUMMARY_LENGTH_CONFIGS = {
        new SummaryLengthConfig(1, 300, 400, "系统概述，需全面覆盖"),
        new SummaryLengthConfig(2, 250, 350, "模块说明，包含主要功能"),
        new SummaryLengthConfig(3, 200, 250, "功能细节，核心流程"),
        new SummaryLengthConfig(4, 150, 200, "具体步骤，实现细节"),
        new SummaryLengthConfig(5, 100, 150, "参数配置，格式说明")
    };

    /**
     * 获取指定层级的摘要长度配置
     *
     * @param level 层级 (1-5)
     * @return 摘要长度配置，如果超出范围则返回 L5 的配置
     */
    public static SummaryLengthConfig getConfig(int level) {
        if (level >= 1 && level <= SUMMARY_LENGTH_CONFIGS.length) {
            return SUMMARY_LENGTH_CONFIGS[level - 1];
        }
        // 默认返回最深层级的配置
        return SUMMARY_LENGTH_CONFIGS[SUMMARY_LENGTH_CONFIGS.length - 1];
    }

    /**
     * 摘要长度配置
     */
    public static class SummaryLengthConfig {
        private final int level;
        private final int minLength;
        private final int maxLength;
        private final String description;

        public SummaryLengthConfig(int level, int minLength, int maxLength, String description) {
            this.level = level;
            this.minLength = minLength;
            this.maxLength = maxLength;
            this.description = description;
        }

        public int getLevel() {
            return level;
        }

        public int getMinLength() {
            return minLength;
        }

        public int getMaxLength() {
            return maxLength;
        }

        public String getDescription() {
            return description;
        }

        @Override
        public String toString() {
            return "L" + level + ": " + minLength + "-" + maxLength + "字 (" + description + ")";
        }
    }
}
