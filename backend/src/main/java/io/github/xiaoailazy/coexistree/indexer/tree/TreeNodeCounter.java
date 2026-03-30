package io.github.xiaoailazy.coexistree.indexer.tree;

import io.github.xiaoailazy.coexistree.indexer.model.TreeNode;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TreeNodeCounter {

    public int count(List<TreeNode> structure) {
        int total = 0;
        for (TreeNode node : structure) {
            total += 1 + count(node.getNodes());
        }
        return total;
    }
}
