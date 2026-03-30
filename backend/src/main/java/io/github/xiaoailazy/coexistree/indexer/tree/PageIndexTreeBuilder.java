package io.github.xiaoailazy.coexistree.indexer.tree;

import io.github.xiaoailazy.coexistree.indexer.model.FlatMarkdownNode;
import io.github.xiaoailazy.coexistree.indexer.model.TreeNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

@Component
public class PageIndexTreeBuilder {

    public List<TreeNode> build(List<FlatMarkdownNode> flatNodes) {
        TreeNodeIdGenerator idGenerator = new TreeNodeIdGenerator();
        List<TreeNode> roots = new ArrayList<>();
        Stack<TreeNode> nodeStack = new Stack<>();
        Stack<Integer> levelStack = new Stack<>();

        for (FlatMarkdownNode flatNode : flatNodes) {
            TreeNode treeNode = new TreeNode();
            treeNode.setNodeId(idGenerator.nextId());
            treeNode.setTitle(flatNode.getTitle());
            treeNode.setLineNum(flatNode.getLineNum());
            treeNode.setLevel(flatNode.getLevel());
            treeNode.setText(flatNode.getText());

            while (!levelStack.isEmpty() && levelStack.peek() >= flatNode.getLevel()) {
                levelStack.pop();
                nodeStack.pop();
            }

            if (nodeStack.isEmpty()) {
                roots.add(treeNode);
            } else {
                nodeStack.peek().getNodes().add(treeNode);
            }

            nodeStack.push(treeNode);
            levelStack.push(flatNode.getLevel());
        }

        return roots;
    }
}
