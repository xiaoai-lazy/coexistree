package io.github.xiaoailazy.coexistree.chat.dto;

import io.github.xiaoailazy.coexistree.review.enums.EvaluationCategory;
import io.github.xiaoailazy.coexistree.review.enums.RiskLevel;
import io.github.xiaoailazy.coexistree.review.model.EvaluationReport;

import java.util.List;

/**
 * 需求评估相关的 SSE 事件
 */
public record EvaluationSseEvent(
        String type,
        String status,
        Object data
) {

    /**
     * 意图检测事件
     */
    public static EvaluationSseEvent intentDetected(String intent, String confidence) {
        return new EvaluationSseEvent("intent_detected", null,
                new IntentData(intent, confidence));
    }

    /**
     * 需要澄清事件
     */
    public static EvaluationSseEvent clarificationNeeded(List<ClarificationOptionData> options) {
        return new EvaluationSseEvent("clarification_needed", null, options);
    }

    /**
     * 评估阶段事件
     */
    public static EvaluationSseEvent evaluationStage(String stage, String status) {
        return new EvaluationSseEvent("evaluation_stage", status, stage);
    }

    /**
     * 评估结果事件
     */
    public static EvaluationSseEvent evaluationResult(EvaluationReport report) {
        return new EvaluationSseEvent("evaluation_result", null, report);
    }

    /**
     * 评估完成事件
     */
    public static EvaluationSseEvent evaluationDone() {
        return new EvaluationSseEvent("evaluation_done", "success", null);
    }

    // 内部数据类

    public record IntentData(String intent, String confidence) {}

    public record ClarificationOptionData(
            String label,
            String description,
            String value,
            String marker
    ) {
        public static ClarificationOptionData of(String label, String description, String value, String marker) {
            return new ClarificationOptionData(label, description, value, marker);
        }
    }

    public record EvaluationReportData(
            String type,
            String category,
            String title,
            String riskLevel,
            String content,
            List<EvaluationReport.EvaluationItem> details
    ) {}
}
