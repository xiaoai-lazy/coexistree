package io.github.xiaoailazy.coexistree.review.service.detector;

import io.github.xiaoailazy.coexistree.knowledge.model.SystemKnowledgeTree;

/**
 * 业务规则一致性检查器
 */
public interface RuleConsistencyChecker {

    /**
     * 检查需求中的业务规则是否与现有规则冲突
     *
     * @param requirementContent 需求文档内容
     * @param systemTree         系统知识树
     * @param previousResponseId 上一次的 LLM responseId（用于维持会话连续性）
     * @return 检测结果（包含报告和 responseId）
     */
    DetectionResult check(String requirementContent, SystemKnowledgeTree systemTree, String previousResponseId);

    /**
     * 检查需求中的业务规则是否与现有规则冲突（简化版）
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
