package io.github.xiaoailazy.coexistree.chat.service.impl;

import io.github.xiaoailazy.coexistree.chat.service.TreeSearchService;
import io.github.xiaoailazy.coexistree.indexer.model.TreeNode;
import io.github.xiaoailazy.coexistree.indexer.model.TreeSearchResult;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Simple keyword-based tree search implementation.
 * Searches node titles, summaries, and text for query keywords.
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
            if (matches(node, lowerQuery)) {
                matchedIds.add(node.getNodeId());
            }
            if (node.getNodes() != null && !node.getNodes().isEmpty()) {
                collectMatching(node.getNodes(), lowerQuery, matchedIds);
            }
        }
    }

    private boolean matches(TreeNode node, String lowerQuery) {
        String[] keywords = lowerQuery.split("\\s+");
        String title = node.getTitle() != null ? node.getTitle().toLowerCase(Locale.ROOT) : "";
        String summary = node.getSummary() != null ? node.getSummary().toLowerCase(Locale.ROOT) : "";
        String text = node.getText() != null ? node.getText().toLowerCase(Locale.ROOT) : "";

        for (String keyword : keywords) {
            if (keyword.length() < 2) continue;
            if (title.contains(keyword) || summary.contains(keyword) || text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
