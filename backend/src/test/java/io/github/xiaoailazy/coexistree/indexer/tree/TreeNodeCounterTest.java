package io.github.xiaoailazy.coexistree.indexer.tree;

import io.github.xiaoailazy.coexistree.indexer.model.TreeNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TreeNodeCounterTest {

    private final TreeNodeCounter counter = new TreeNodeCounter();

    @Test
    void shouldCountSingleNode() {
        TreeNode node = new TreeNode();
        node.setNodeId("0001");

        int result = counter.count(List.of(node));

        assertThat(result).isEqualTo(1);
    }

    @Test
    void shouldCountMultipleRoots() {
        TreeNode node1 = new TreeNode();
        node1.setNodeId("0001");
        TreeNode node2 = new TreeNode();
        node2.setNodeId("0002");

        int result = counter.count(List.of(node1, node2));

        assertThat(result).isEqualTo(2);
    }

    @Test
    void shouldCountNestedNodes() {
        TreeNode child = new TreeNode();
        child.setNodeId("0002");
        TreeNode parent = new TreeNode();
        parent.setNodeId("0001");
        parent.setNodes(List.of(child));

        int result = counter.count(List.of(parent));

        assertThat(result).isEqualTo(2);
    }

    @Test
    void shouldCountDeepNesting() {
        TreeNode level3 = new TreeNode();
        level3.setNodeId("0003");
        TreeNode level2 = new TreeNode();
        level2.setNodeId("0002");
        level2.setNodes(List.of(level3));
        TreeNode level1 = new TreeNode();
        level1.setNodeId("0001");
        level1.setNodes(List.of(level2));

        int result = counter.count(List.of(level1));

        assertThat(result).isEqualTo(3);
    }

    @Test
    void shouldReturnZeroForEmptyList() {
        int result = counter.count(List.of());

        assertThat(result).isZero();
    }

    @Test
    void shouldCountComplexTree() {
        TreeNode leaf1 = new TreeNode();
        leaf1.setNodeId("0004");
        TreeNode leaf2 = new TreeNode();
        leaf2.setNodeId("0005");
        TreeNode branch1 = new TreeNode();
        branch1.setNodeId("0002");
        branch1.setNodes(List.of(leaf1));
        TreeNode branch2 = new TreeNode();
        branch2.setNodeId("0003");
        branch2.setNodes(List.of(leaf2));
        TreeNode root = new TreeNode();
        root.setNodeId("0001");
        root.setNodes(List.of(branch1, branch2));

        int result = counter.count(List.of(root));

        assertThat(result).isEqualTo(5);
    }

    @Test
    void shouldCountWideTree() {
        TreeNode child1 = new TreeNode();
        child1.setNodeId("0002");
        TreeNode child2 = new TreeNode();
        child2.setNodeId("0003");
        TreeNode child3 = new TreeNode();
        child3.setNodeId("0004");
        TreeNode parent = new TreeNode();
        parent.setNodeId("0001");
        parent.setNodes(List.of(child1, child2, child3));

        int result = counter.count(List.of(parent));

        assertThat(result).isEqualTo(4);
    }
}
