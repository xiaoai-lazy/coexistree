package io.github.xiaoailazy.coexistree.indexer.tree;

import io.github.xiaoailazy.coexistree.indexer.model.TreeNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TreeNodeMapperTest {

    private final TreeNodeMapper mapper = new TreeNodeMapper();

    @Test
    void shouldCreateMapFromFlatTree() {
        TreeNode root = createNode("0001", "Root");

        Map<String, TreeNode> result = mapper.createNodeMap(List.of(root));

        assertThat(result).hasSize(1);
        assertThat(result.get("0001")).isEqualTo(root);
    }

    @Test
    void shouldCreateMapFromNestedTree() {
        TreeNode child = createNode("0002", "Child");
        TreeNode parent = createNode("0001", "Parent");
        parent.setNodes(List.of(child));

        Map<String, TreeNode> result = mapper.createNodeMap(List.of(parent));

        assertThat(result).hasSize(2);
        assertThat(result.get("0001")).isEqualTo(parent);
        assertThat(result.get("0002")).isEqualTo(child);
    }

    @Test
    void shouldCreateMapFromMultipleRoots() {
        TreeNode root1 = createNode("0001", "Root1");
        TreeNode root2 = createNode("0002", "Root2");

        Map<String, TreeNode> result = mapper.createNodeMap(List.of(root1, root2));

        assertThat(result).hasSize(2);
        assertThat(result.get("0001")).isEqualTo(root1);
        assertThat(result.get("0002")).isEqualTo(root2);
    }

    @Test
    void shouldHandleEmptyTree() {
        Map<String, TreeNode> result = mapper.createNodeMap(List.of());

        assertThat(result).isEmpty();
    }

    @Test
    void shouldHandleDeepNesting() {
        TreeNode level3 = createNode("0003", "Level3");
        TreeNode level2 = createNode("0002", "Level2");
        level2.setNodes(List.of(level3));
        TreeNode level1 = createNode("0001", "Level1");
        level1.setNodes(List.of(level2));

        Map<String, TreeNode> result = mapper.createNodeMap(List.of(level1));

        assertThat(result).hasSize(3);
        assertThat(result.get("0001").getTitle()).isEqualTo("Level1");
        assertThat(result.get("0002").getTitle()).isEqualTo("Level2");
        assertThat(result.get("0003").getTitle()).isEqualTo("Level3");
    }

    @Test
    void shouldHandleComplexTree() {
        TreeNode leaf1 = createNode("0004", "Leaf1");
        TreeNode leaf2 = createNode("0005", "Leaf2");
        TreeNode branch1 = createNode("0002", "Branch1");
        branch1.setNodes(List.of(leaf1));
        TreeNode branch2 = createNode("0003", "Branch2");
        branch2.setNodes(List.of(leaf2));
        TreeNode root = createNode("0001", "Root");
        root.setNodes(List.of(branch1, branch2));

        Map<String, TreeNode> result = mapper.createNodeMap(List.of(root));

        assertThat(result).hasSize(5);
        assertThat(result.get("0001")).isNotNull();
        assertThat(result.get("0002")).isNotNull();
        assertThat(result.get("0003")).isNotNull();
        assertThat(result.get("0004")).isNotNull();
        assertThat(result.get("0005")).isNotNull();
    }

    private TreeNode createNode(String nodeId, String title) {
        TreeNode node = new TreeNode();
        node.setNodeId(nodeId);
        node.setTitle(title);
        return node;
    }
}
