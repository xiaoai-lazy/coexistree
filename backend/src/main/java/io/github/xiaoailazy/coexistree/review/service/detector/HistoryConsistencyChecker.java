package io.github.xiaoailazy.coexistree.review.service.detector;

import io.github.xiaoailazy.coexistree.knowledge.model.SystemKnowledgeTree;

/**
 * 历史背景/现状一致性检查器
 */
public interface HistoryConsistencyChecker {

    /**
     * 验证需求描述的历史背景和系统现状是否准确
     *
     * @param requirementContent 需求文档内容
     * @param systemTree         系统知识树
     * @param previousResponseId 上一次的 LLM responseId（用于维持会话连续性）
     * @return 检测结果（包含报告和 responseId）
     */
    DetectionResult check(String requirementContent, SystemKnowledgeTree systemTree, String previousResponseId);

    /**
     * 验证需求描述的历史背景和系统现状是否准确（简化版）
     * 不提供 previousResponseId，适用于单次检测场景
     *
     * @param requirementContent 需求文档内容
     * @param systemTree         系统知识树
     * @return 检测结果（包含报告和 responseId）
     */
    default DetectionResult check(String requirementContent, SystemKnowledgeTree systemTree) {
        return check(requirementContent, systemTree, null);
    }
}
