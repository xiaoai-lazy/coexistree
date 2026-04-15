package io.github.xiaoailazy.coexistree.review.service.detector;

import io.github.xiaoailazy.coexistree.knowledge.model.SystemKnowledgeTree;

/**
 * 模块影响分析器
 */
public interface ModuleImpactAnalyzer {

    /**
     * 分析需求涉及的功能模块及影响范围
     *
     * @param requirementContent 需求文档内容
     * @param systemTree         系统知识树
     * @param previousResponseId 上一次的 LLM responseId（用于维持会话连续性）
     * @return 检测结果（包含报告和 responseId）
     */
    DetectionResult analyze(String requirementContent, SystemKnowledgeTree systemTree, String previousResponseId);

    /**
     * 分析需求涉及的功能模块及影响范围（简化版）
     * 不提供 previousResponseId，适用于单次检测场景
     *
     * @param requirementContent 需求文档内容
     * @param systemTree         系统知识树
     * @return 检测结果（包含报告和 responseId）
     */
    default DetectionResult analyze(String requirementContent, SystemKnowledgeTree systemTree) {
        return analyze(requirementContent, systemTree, null);
    }
}
