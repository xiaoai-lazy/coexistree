package io.github.xiaoailazy.coexistree.review.model;

import io.github.xiaoailazy.coexistree.review.enums.EvaluationCategory;
import io.github.xiaoailazy.coexistree.review.enums.RiskLevel;

import java.util.List;

/**
 * 评估报告
 */
public record EvaluationReport(
        /**
         * 评估类别
         */
        EvaluationCategory category,

        /**
         * 风险等级
         */
        RiskLevel riskLevel,

        /**
         * 标题
         */
        String title,

        /**
         * 副标题（可选）
         */
        String subtitle,

        /**
         * 总结
         */
        String summary,

        /**
         * 详细项目列表
         */
        List<EvaluationItem> details,

        /**
         * 操作按钮（可选）
         */
        List<ReportAction> actions
) {

    /**
     * 评估项目
     */
    public record EvaluationItem(
            /**
             * 项目名称
             */
            String name,

            /**
             * 描述
             */
            String description,

            /**
             * 严重程度
             */
            String severity,

            /**
             * 建议（可选）
             */
            String suggestion
    ) {}

    /**
     * 报告操作
     */
    public record ReportAction(
            /**
             * 按钮标签
             */
            String label,

            /**
             * 按钮类型
             */
            String type,

            /**
             * 是否为链接样式
             */
            boolean link,

            /**
             * 操作处理器标识
             */
            String handler
    ) {}

    /**
     * 创建简单的评估报告
     */
    public static EvaluationReport of(EvaluationCategory category, RiskLevel riskLevel,
                                       String title, String summary) {
        return new EvaluationReport(category, riskLevel, title, null, summary, List.of(), null);
    }

    /**
     * 检查是否有发现
     */
    public boolean hasFindings() {
        return details != null && !details.isEmpty();
    }

    /**
     * 获取类别显示名称
     */
    public String categoryDisplayName() {
        return switch (category) {
            case CONFLICT -> "功能冲突检测";
            case CONSISTENCY -> "业务规则一致性";
            case IMPACT -> "依赖模块识别";
            case HISTORY -> "历史背景/现状一致性";
        };
    }

    /**
     * 获取风险等级显示名称
     */
    public String riskLevelDisplayName() {
        return switch (riskLevel) {
            case HIGH -> "高风险";
            case MEDIUM -> "中风险";
            case LOW -> "低风险";
            case NONE -> "无风险";
        };
    }
}
