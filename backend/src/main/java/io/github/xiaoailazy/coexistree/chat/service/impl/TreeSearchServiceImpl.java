package io.github.xiaoailazy.coexistree.chat.service.impl;

import io.github.xiaoailazy.coexistree.chat.service.TreeSearchService;
import io.github.xiaoailazy.coexistree.featuretree.model.FeatureCurrentState;
import io.github.xiaoailazy.coexistree.indexer.model.TreeNode;
import io.github.xiaoailazy.coexistree.indexer.model.TreeSearchResult;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 关键词检索：对 FEATURE/MODULE 使用 title、summary、{@code currentState} 可检索文本；不将 {@code text} 作为主匹配字段。
 * SYSTEM_ROOT / FEATURE_ROOT 节点自身不参与命中（§18.3），仍向下遍历子树。
 */
@Service
public class TreeSearchServiceImpl implements TreeSearchService {

    @Override
    public TreeSearchResult search(List<TreeNode> structure, String query, String model) {
        return search(structure, query, model, null);
    }

    @Override
    public TreeSearchResult search(List<TreeNode> structure, String query, String model, String previousResponseId) {
        List<String> matchedNodeIds = new ArrayList<>();
        if (structure != null && query != null && !query.isBlank()) {
            String lowerQuery = query.toLowerCase(Locale.ROOT);
            collectMatching(structure, lowerQuery, matchedNodeIds);
        }
        return new TreeSearchResult(null, null, matchedNodeIds);
    }

    private void collectMatching(List<TreeNode> nodes, String lowerQuery, List<String> matchedIds) {
        for (TreeNode node : nodes) {
            if (!isStructuralRoot(node) && matches(node, lowerQuery)) {
                matchedIds.add(node.getNodeId());
            }
            if (node.getNodes() != null && !node.getNodes().isEmpty()) {
                collectMatching(node.getNodes(), lowerQuery, matchedIds);
            }
        }
    }

    private static boolean isStructuralRoot(TreeNode node) {
        String t = node.getNodeType();
        return "SYSTEM_ROOT".equals(t) || "FEATURE_ROOT".equals(t);
    }

    private boolean matches(TreeNode node, String lowerQuery) {
        if (isStructuralRoot(node)) {
            return false;
        }
        String[] keywords = lowerQuery.split("\\s+");
        String title = node.getTitle() != null ? node.getTitle().toLowerCase(Locale.ROOT) : "";
        String summary = node.getSummary() != null ? node.getSummary().toLowerCase(Locale.ROOT) : "";
        String state = currentStateSearchText(node.getCurrentState()).toLowerCase(Locale.ROOT);
        String type = node.getNodeType();
        boolean featureOrModule = "FEATURE".equals(type) || "MODULE".equals(type);
        String text = "";
        if (!featureOrModule && node.getText() != null) {
            text = node.getText().toLowerCase(Locale.ROOT);
        }

        for (String keyword : keywords) {
            if (keyword.length() < 2) {
                continue;
            }
            if (title.contains(keyword)
                    || summary.contains(keyword)
                    || state.contains(keyword)
                    || text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private static String currentStateSearchText(FeatureCurrentState s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        append(sb, s.getRequirementSummary());
        append(sb, s.getDesignSummary());
        append(sb, s.getImplementationLogic());
        append(sb, s.getConstraints());
        append(sb, s.getLatestChangeSummary());
        append(sb, s.getStatus());
        append(sb, s.getConfidence());
        append(sb, s.getUpdatedAt());
        return sb.toString();
    }

    private static void append(StringBuilder sb, String part) {
        if (part != null && !part.isBlank()) {
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(part);
        }
    }
}
