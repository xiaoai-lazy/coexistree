package io.github.xiaoailazy.coexistree.agent.tools;

import io.github.xiaoailazy.coexistree.agent.context.AgentUserContext;
import io.github.xiaoailazy.coexistree.knowledge.model.SystemKnowledgeTree;
import io.github.xiaoailazy.coexistree.knowledge.service.SystemKnowledgeTreeService;
import io.github.xiaoailazy.coexistree.chat.service.TreeSearchService;
import io.github.xiaoailazy.coexistree.indexer.model.TreeNode;
import io.github.xiaoailazy.coexistree.indexer.model.NodeSource;
import io.github.xiaoailazy.coexistree.indexer.model.TreeSearchResult;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 搜索知识树中的相关节点。
 * Tool 层强制权限校验：按用户 viewLevel 过滤结果。
 */
@Slf4j
public class SearchTreeTool {

    private final SystemKnowledgeTreeService treeService;
    private final TreeSearchService searchService;

    public SearchTreeTool(
            SystemKnowledgeTreeService treeService,
            TreeSearchService searchService
    ) {
        this.treeService = treeService;
        this.searchService = searchService;
    }

    /**
     * 在指定系统的知识树中搜索节点。
     *
     * @param context 用户上下文（包含 systemId 和 viewLevel）
     * @param query   搜索查询
     * @return 搜索结果摘要，或错误信息
     */
    public String execute(AgentUserContext context, String query) {
        try {
            SystemKnowledgeTree tree = treeService.getActiveTree(context.systemId());
            List<TreeNode> structure = tree.getStructure();

            // 执行搜索
            TreeSearchResult result = searchService.search(
                    structure, query, null, null);

            // 按权限过滤节点
            List<TreeNode> filteredNodes = filterBySecurityLevel(
                    structure, result.getNodeList(), context.viewLevel());

            if (filteredNodes.isEmpty()) {
                return "未找到与 \"" + query + "\" 相关的可访问节点。";
            }

            // 返回节点摘要供 Agent 参考
            String summary = filteredNodes.stream()
                    .map(n -> "- nodeId: " + n.getNodeId() +
                            ", title: " + n.getTitle() +
                            ", level: " + n.getLevel())
                    .collect(Collectors.joining("\n"));

            return "找到 " + filteredNodes.size() + " 个相关节点:\n" + summary;

        } catch (Exception e) {
            log.error("search_tree 执行失败, systemId={}, query={}", context.systemId(), query, e);
            return "搜索失败: " + e.getMessage();
        }
    }

    private List<TreeNode> filterBySecurityLevel(
            List<TreeNode> structure,
            List<String> nodeIds,
            Integer viewLevel
    ) {
        Map<String, TreeNode> nodeMap = buildNodeMap(structure);
        int level = (viewLevel != null) ? viewLevel : Integer.MAX_VALUE;

        List<TreeNode> result = new ArrayList<>();
        for (String nodeId : nodeIds) {
            TreeNode node = nodeMap.get(nodeId);
            if (node != null) {
                if (node.getSources() != null) {
                    List<NodeSource> filtered = node.getSources().stream()
                            .filter(s -> s.getSecurityLevel() == null || s.getSecurityLevel() <= level)
                            .toList();
                    node.setSources(filtered);
                }
                if (node.getSources() == null || !node.getSources().isEmpty()) {
                    result.add(node);
                }
            }
        }
        return result;
    }

    private Map<String, TreeNode> buildNodeMap(List<TreeNode> structure) {
        return structure.stream()
                .collect(Collectors.toMap(TreeNode::getNodeId, n -> n, (a, b) -> a));
    }
}
