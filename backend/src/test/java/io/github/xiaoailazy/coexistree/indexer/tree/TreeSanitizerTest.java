package io.github.xiaoailazy.coexistree.indexer.tree;

import io.github.xiaoailazy.coexistree.indexer.model.TreeNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TreeSanitizerTest {

    private final TreeSanitizer sanitizer = new TreeSanitizer();

    @Test
    void shouldRemoveTextFromSingleNode() {
        TreeNode node = createNode("0001", "Title", "Text content");

        List<TreeNode> result = sanitizer.removeText(List.of(node));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getNodeId()).isEqualTo("0001");
        assertThat(result.get(0).getTitle()).isEqualTo("Title");
        assertThat(result.get(0).getText()).isNull();
    }

    @Test
    void shouldPreserveOtherFields() {
        TreeNode node = createNode("0001", "Title", "Text");
        node.setLineNum(10);
        node.setLevel(2);
        node.setSummary("Summary");
        node.setPrefixSummary("Prefix");

        List<TreeNode> result = sanitizer.removeText(List.of(node));

        TreeNode sanitized = result.get(0);
        assertThat(sanitized.getNodeId()).isEqualTo("0001");
        assertThat(sanitized.getTitle()).isEqualTo("Title");
        assertThat(sanitized.getLineNum()).isEqualTo(10);
        assertThat(sanitized.getLevel()).isEqualTo(2);
        assertThat(sanitized.getSummary()).isEqualTo("Summary");
        assertThat(sanitized.getPrefixSummary()).isEqualTo("Prefix");
        assertThat(sanitized.getText()).isNull();
    }

    @Test
    void shouldRemoveTextFromNestedNodes() {
        TreeNode child = createNode("0002", "Child", "Child text");
        TreeNode parent = createNode("0001", "Parent", "Parent text");
        parent.setNodes(List.of(child));

        List<TreeNode> result = sanitizer.removeText(List.of(parent));

        assertThat(result.get(0).getText()).isNull();
        assertThat(result.get(0).getNodes().get(0).getText()).isNull();
    }

    @Test
    void shouldHandleEmptyInput() {
        List<TreeNode> result = sanitizer.removeText(List.of());

        assertThat(result).isEmpty();
    }

    @Test
    void shouldNotModifyOriginalTree() {
        TreeNode original = createNode("0001", "Title", "Original text");

        sanitizer.removeText(List.of(original));

        assertThat(original.getText()).isEqualTo("Original text");
    }

    @Test
    void shouldHandleMultipleRoots() {
        TreeNode root1 = createNode("0001", "Root1", "Text1");
        TreeNode root2 = createNode("0002", "Root2", "Text2");

        List<TreeNode> result = sanitizer.removeText(List.of(root1, root2));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getText()).isNull();
        assertThat(result.get(1).getText()).isNull();
    }

    @Test
    void shouldHandleDeepNesting() {
        TreeNode level3 = createNode("0003", "Level3", "Text3");
        TreeNode level2 = createNode("0002", "Level2", "Text2");
        level2.setNodes(List.of(level3));
        TreeNode level1 = createNode("0001", "Level1", "Text1");
        level1.setNodes(List.of(level2));

        List<TreeNode> result = sanitizer.removeText(List.of(level1));

        assertThat(result.get(0).getText()).isNull();
        assertThat(result.get(0).getNodes().get(0).getText()).isNull();
        assertThat(result.get(0).getNodes().get(0).getNodes().get(0).getText()).isNull();
    }

    private TreeNode createNode(String nodeId, String title, String text) {
        TreeNode node = new TreeNode();
        node.setNodeId(nodeId);
        node.setTitle(title);
        node.setText(text);
        return node;
    }
}
