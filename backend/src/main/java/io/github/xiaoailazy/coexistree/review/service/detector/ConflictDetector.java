package io.github.xiaoailazy.coexistree.review.service.detector;

import io.github.xiaoailazy.coexistree.review.model.EvaluationReport;
import io.github.xiaoailazy.coexistree.knowledge.model.SystemKnowledgeTree;

/**
 * 功能冲突检测器
 */
public interface ConflictDetector {

    /**
     * 检测需求中的功能点与现有功能是否存在冲突
     *
     * @param requirementContent 需求文档内容
     * @param systemTree         系统知识树
     * @param previousResponseId 上一次的 LLM responseId（用于维持会话连续性）
     * @return 检测结果（包含报告和 responseId）
     */
    DetectionResult detect(String requirementContent, SystemKnowledgeTree systemTree, String previousResponseId);

    /**
     * 检测需求中的功能点与现有功能是否存在冲突（简化版）
     * 不提供 previousResponseId，适用于单次检测场景
     *
     * @param requirementContent 需求文档内容
     * @param systemTree         系统知识树
     * @return 检测结果（包含报告和 responseId）
     */
    default DetectionResult detect(String requirementContent, SystemKnowledgeTree systemTree) {
        return detect(requirementContent, systemTree, null);
    }
}
