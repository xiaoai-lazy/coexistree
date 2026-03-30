package io.github.xiaoailazy.coexistree.knowledge.service;

import io.github.xiaoailazy.coexistree.knowledge.model.SystemKnowledgeTree;
import io.github.xiaoailazy.coexistree.indexer.model.DocumentTree;
import io.github.xiaoailazy.coexistree.system.entity.SystemEntity;

/**
 * 系统知识树服务接口
 * 
 * 定义系统知识树的核心操作：
 * - 获取活跃的系统知识树（用于问答）
 * - 基线合并（首次建树，LLM 驱动）
 * - 变更合并（更新现有树，LLM 驱动）
 */
public interface SystemKnowledgeTreeService {

    /**
     * 获取活跃的系统知识树
     * 
     * @param systemId 系统 ID
     * @return 系统知识树
     * @throws io.github.xiaoailazy.coexistree.common.exception.BusinessException 
     *         当系统树不存在或状态不为 ACTIVE 时抛出
     */
    SystemKnowledgeTree getActiveTree(Long systemId);

    /**
     * 基线合并：从基线文档创建或重建系统知识树
     * 
     * 处理流程：
     * 1. 检查系统树是否已存在且状态为 ACTIVE
     *    - 若已存在 → 走变更合并流程（同 mergeChange）
     *    - 若不存在 → 创建新树
     * 2. 调用 LLM 分析文档树，生成系统架构
     * 3. 为每个节点分配 nodeId、设置 sources、创建 provenance
     * 4. 写入系统树 JSON 文件
     * 5. 保存 system_knowledge_trees 记录（status=ACTIVE）
     * 
     * @param documentId 文档 ID
     * @param docTree 文档树
     * @param system 系统实体
     */
    void mergeBaseline(Long documentId, DocumentTree docTree, SystemEntity system);

    /**
     * 变更合并：将变更文档合并到现有系统知识树
     * 
     * 处理流程：
     * 1. 加载现有系统树（读锁）
     * 2. 调用 LLM 生成合并指令（UPDATE/CREATE 操作）
     * 3. 执行合并指令（写锁）
     * 4. 原子写入系统树 JSON 文件
     * 5. 更新 system_knowledge_trees.tree_version +1
     * 
     * @param documentId 文档 ID
     * @param docTree 文档树
     * @param system 系统实体
     */
    void mergeChange(Long documentId, DocumentTree docTree, SystemEntity system);
}
