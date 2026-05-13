package io.github.xiaoailazy.coexistree.indexer.tree;

import io.github.xiaoailazy.coexistree.indexer.model.SimplifiedTreeNode;
import io.github.xiaoailazy.coexistree.indexer.model.TreeNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Converts full TreeNode trees into SimplifiedTreeNode trees for LLM prompts,
 * stripping fields that are irrelevant noise (text, lineNum, sources, provenance).
 */
@Component
public class TreeSimplifier {

    public List<SimplifiedTreeNode> simplify(List<TreeNode> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return new ArrayList<>();
        }
        List<SimplifiedTreeNode> result = new ArrayList<>(nodes.size());
        for (TreeNode node : nodes) {
            SimplifiedTreeNode s = new SimplifiedTreeNode();
            s.setNodeId(node.getNodeId());
            s.setTitle(node.getTitle());
            s.setSummary(node.getSummary());
            s.setPrefixSummary(node.getPrefixSummary());
            s.setChildren(simplify(node.getNodes()));
            result.add(s);
        }
        return result;
    }
}
