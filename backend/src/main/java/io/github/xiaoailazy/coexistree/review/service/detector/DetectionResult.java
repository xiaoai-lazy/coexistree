package io.github.xiaoailazy.coexistree.review.service.detector;

import io.github.xiaoailazy.coexistree.review.model.EvaluationReport;

/**
 * 检测结果 - 包含评估报告和 LLM responseId
 */
public record DetectionResult(
        EvaluationReport report,
        String responseId
) {
    public static DetectionResult of(EvaluationReport report, String responseId) {
        return new DetectionResult(report, responseId);
    }
}
