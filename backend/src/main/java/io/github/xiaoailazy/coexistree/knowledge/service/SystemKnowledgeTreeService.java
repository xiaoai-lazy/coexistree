package io.github.xiaoailazy.coexistree.knowledge.service;

import io.github.xiaoailazy.coexistree.knowledge.model.SystemKnowledgeTree;

/**
 * 系统知识树服务：读路径以 ACTIVE 行为准；写入由 {@link SystemTreeUpdateService#applyChange(long)} 等编排完成。
 */
public interface SystemKnowledgeTreeService {

    /**
     * 获取活跃的系统知识树（tree_status=ACTIVE）。
     *
     * @throws io.github.xiaoailazy.coexistree.shared.exception.BusinessException 当系统树不存在或状态不为 ACTIVE
     */
    SystemKnowledgeTree getActiveTree(Long systemId);
}
