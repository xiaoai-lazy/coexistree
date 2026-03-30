package io.github.xiaoailazy.coexistree.review.service;

import io.github.xiaoailazy.coexistree.review.model.EvaluationReport;
import io.github.xiaoailazy.coexistree.knowledge.model.SystemKnowledgeTree;

import java.util.List;
import java.util.function.Consumer;

/**
 * 需求评估服务 - 协调四项检测
 */
public interface RequirementEvaluationService {

    /**
     * 执行完整的需求评估
     *
     * @param requirementContent 需求文档内容
     * @param systemTree         系统知识树
     * @param onProgress         进度回调（用于 SSE 推送）
     * @param previousResponseId 上一次的 LLM responseId（用于维持会话连续性）
     * @return 评估结果（包含报告列表和最后的 responseId）
     */
    EvaluationResult evaluate(String requirementContent,
                              SystemKnowledgeTree systemTree,
                              Consumer<EvaluationStage> onProgress,
                              String previousResponseId);

    /**
     * 执行单项检测
     *
     * @param requirementContent 需求文档内容
     * @param systemTree         系统知识树
     * @param stage              检测阶段
     * @return 该阶段的评估报告
     */
    EvaluationReport evaluateStage(String requirementContent,
                                   SystemKnowledgeTree systemTree,
                                   EvaluationStage stage);

    /**
     * 评估结果
     */
    record EvaluationResult(
            List<EvaluationReport> reports,
            String lastResponseId
    ) {
        public static EvaluationResult of(List<EvaluationReport> reports, String lastResponseId) {
            return new EvaluationResult(reports, lastResponseId);
        }
    }

    /**
     * 评估阶段枚举
     */
    enum EvaluationStage {
        CONFLICT_DETECTION("CONFLICT", "功能冲突检测"),
        CONSISTENCY_CHECK("CONSISTENCY", "业务规则一致性检查"),
        IMPACT_ANALYSIS("IMPACT", "依赖模块识别"),
        HISTORY_CHECK("HISTORY", "历史背景/现状一致性检查");

        private final String category;
        private final String displayName;

        EvaluationStage(String category, String displayName) {
            this.category = category;
            this.displayName = displayName;
        }

        public String getCategory() {
            return category;
        }

        public String getDisplayName() {
            return displayName;
        }
    }
}
