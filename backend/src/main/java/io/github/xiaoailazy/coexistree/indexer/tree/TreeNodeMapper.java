package io.github.xiaoailazy.coexistree.indexer.tree;

import io.github.xiaoailazy.coexistree.indexer.model.TreeNode;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class TreeNodeMapper {

    public Map<String, TreeNode> createNodeMap(List<TreeNode> structure) {
        Map<String, TreeNode> result = new HashMap<>();
        walk(structure, result);
        return result;
    }

    private void walk(List<TreeNode> nodes, Map<String, TreeNode> target) {
        for (TreeNode node : nodes) {
            target.put(node.getNodeId(), node);
            walk(node.getNodes(), target);
        }
    }
}

