package io.github.xiaoailazy.coexistree.indexer.tree;

import io.github.xiaoailazy.coexistree.indexer.model.FlatMarkdownNode;
import io.github.xiaoailazy.coexistree.indexer.model.TreeNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PageIndexTreeBuilderTest {

    private final PageIndexTreeBuilder builder = new PageIndexTreeBuilder();

    @Test
    void shouldBuildFlatTreeWithSingleRoot() {
        List<FlatMarkdownNode> flatNodes = List.of(
                createFlatNode("Root", 1, 1, "Root content")
        );

        List<TreeNode> result = builder.build(flatNodes);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTitle()).isEqualTo("Root");
        assertThat(result.get(0).getNodeId()).isEqualTo("0001");
        assertThat(result.get(0).getNodes()).isEmpty();
    }

    @Test
    void shouldBuildTreeWithNestedChildren() {
        List<FlatMarkdownNode> flatNodes = List.of(
                createFlatNode("Parent", 1, 1, "Parent content"),
                createFlatNode("Child", 2, 2, "Child content")
        );

        List<TreeNode> result = builder.build(flatNodes);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTitle()).isEqualTo("Parent");
        assertThat(result.get(0).getNodeId()).isEqualTo("0001");
        assertThat(result.get(0).getNodes()).hasSize(1);
        assertThat(result.get(0).getNodes().get(0).getTitle()).isEqualTo("Child");
        assertThat(result.get(0).getNodes().get(0).getNodeId()).isEqualTo("0002");
    }

    @Test
    void shouldBuildTreeWithMultipleRoots() {
        List<FlatMarkdownNode> flatNodes = List.of(
                createFlatNode("First", 1, 1, "First content"),
                createFlatNode("Second", 1, 2, "Second content")
        );

        List<TreeNode> result = builder.build(flatNodes);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getTitle()).isEqualTo("First");
        assertThat(result.get(1).getTitle()).isEqualTo("Second");
    }

    @Test
    void shouldHandleDeepNesting() {
        List<FlatMarkdownNode> flatNodes = List.of(
                createFlatNode("H1", 1, 1, "H1 content"),
                createFlatNode("H2", 2, 2, "H2 content"),
                createFlatNode("H3", 3, 3, "H3 content")
        );

        List<TreeNode> result = builder.build(flatNodes);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTitle()).isEqualTo("H1");
        assertThat(result.get(0).getNodes()).hasSize(1);
        assertThat(result.get(0).getNodes().get(0).getTitle()).isEqualTo("H2");
        assertThat(result.get(0).getNodes().get(0).getNodes()).hasSize(1);
        assertThat(result.get(0).getNodes().get(0).getNodes().get(0).getTitle()).isEqualTo("H3");
    }

    @Test
    void shouldHandleMixedLevels() {
        List<FlatMarkdownNode> flatNodes = List.of(
                createFlatNode("Chapter1", 1, 1, "Chapter1"),
                createFlatNode("Section1", 2, 2, "Section1"),
                createFlatNode("Section2", 2, 3, "Section2"),
                createFlatNode("Chapter2", 1, 4, "Chapter2")
        );

        List<TreeNode> result = builder.build(flatNodes);

        assertThat(result).hasSize(2);
        
        assertThat(result.get(0).getTitle()).isEqualTo("Chapter1");
        assertThat(result.get(0).getNodes()).hasSize(2);
        assertThat(result.get(0).getNodes().get(0).getTitle()).isEqualTo("Section1");
        assertThat(result.get(0).getNodes().get(1).getTitle()).isEqualTo("Section2");
        
        assertThat(result.get(1).getTitle()).isEqualTo("Chapter2");
        assertThat(result.get(1).getNodes()).isEmpty();
    }

    @Test
    void shouldHandleEmptyInput() {
        List<FlatMarkdownNode> flatNodes = List.of();

        List<TreeNode> result = builder.build(flatNodes);

        assertThat(result).isEmpty();
    }

    @Test
    void shouldResetIdGeneratorOnEachBuild() {
        List<FlatMarkdownNode> flatNodes = List.of(
                createFlatNode("Node", 1, 1, "Content")
        );

        List<TreeNode> result1 = builder.build(flatNodes);
        List<TreeNode> result2 = builder.build(flatNodes);

        assertThat(result1.get(0).getNodeId()).isEqualTo("0001");
        assertThat(result2.get(0).getNodeId()).isEqualTo("0001");
    }

    @Test
    void shouldSetLineNumAndText() {
        List<FlatMarkdownNode> flatNodes = List.of(
                createFlatNode("Title", 1, 5, "Some text content")
        );

        List<TreeNode> result = builder.build(flatNodes);

        assertThat(result.get(0).getLineNum()).isEqualTo(5);
        assertThat(result.get(0).getText()).isEqualTo("Some text content");
    }

    private FlatMarkdownNode createFlatNode(String title, int level, int lineNum, String text) {
        FlatMarkdownNode node = new FlatMarkdownNode();
        node.setTitle(title);
        node.setLevel(level);
        node.setLineNum(lineNum);
        node.setText(text);
        return node;
    }
}
