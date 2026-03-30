package io.github.xiaoailazy.coexistree.indexer.storage;

import io.github.xiaoailazy.coexistree.shared.enums.ErrorCode;
import io.github.xiaoailazy.coexistree.shared.exception.BusinessException;
import io.github.xiaoailazy.coexistree.shared.util.JsonUtils;
import io.github.xiaoailazy.coexistree.indexer.model.DocumentTree;
import io.github.xiaoailazy.coexistree.indexer.model.TreeNode;
import io.github.xiaoailazy.coexistree.indexer.tree.TreeNodeCounter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TreeFileLoaderTest {

    @Mock
    private JsonUtils jsonUtils;
    @Mock
    private TreeNodeCounter treeNodeCounter;

    private TreeFileLoader treeFileLoader;

    @BeforeEach
    void setUp() {
        treeFileLoader = new TreeFileLoader(jsonUtils, treeNodeCounter);
    }

    @Test
    void testLoadSuccess(@TempDir Path tempDir) throws IOException {
        // Given
        Path treeFile = tempDir.resolve("tree.json");
        String jsonContent = "{\"docName\":\"test\",\"docDescription\":\"测试文档\"}";
        Files.writeString(treeFile, jsonContent, StandardCharsets.UTF_8);

        DocumentTree expectedTree = new DocumentTree();
        expectedTree.setDocName("test");
        expectedTree.setDocDescription("测试文档");
        expectedTree.setStructure(List.of());

        when(jsonUtils.fromJson(jsonContent, DocumentTree.class)).thenReturn(expectedTree);
        when(treeNodeCounter.count(expectedTree.getStructure())).thenReturn(0);

        // When
        DocumentTree result = treeFileLoader.load(treeFile);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getDocName()).isEqualTo("test");
        assertThat(result.getDocDescription()).isEqualTo("测试文档");
    }

    @Test
    void testLoadWithStructure(@TempDir Path tempDir) throws IOException {
        // Given
        Path treeFile = tempDir.resolve("tree.json");
        String jsonContent = "{\"docName\":\"guide\",\"structure\":[{\"nodeId\":\"1\",\"heading\":\"标题\"}]}";
        Files.writeString(treeFile, jsonContent, StandardCharsets.UTF_8);

        TreeNode node = new TreeNode();
        node.setNodeId("1");
        node.setTitle("标题");

        DocumentTree expectedTree = new DocumentTree();
        expectedTree.setDocName("guide");
        expectedTree.setStructure(List.of(node));

        when(jsonUtils.fromJson(jsonContent, DocumentTree.class)).thenReturn(expectedTree);
        when(treeNodeCounter.count(expectedTree.getStructure())).thenReturn(1);

        // When
        DocumentTree result = treeFileLoader.load(treeFile);

        // Then
        assertThat(result.getDocName()).isEqualTo("guide");
        assertThat(result.getStructure()).hasSize(1);
        assertThat(result.getStructure().get(0).getNodeId()).isEqualTo("1");
    }

    @Test
    void testLoadFileNotFound(@TempDir Path tempDir) {
        // Given
        Path nonExistentFile = tempDir.resolve("non-existent.json");

        // Then
        assertThatThrownBy(() -> treeFileLoader.load(nonExistentFile))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(ErrorCode.TREE_FILE_NOT_FOUND);
                })
                .hasMessageContaining("Tree file not found");
    }

    @Test
    void testLoadWithNestedDirectory(@TempDir Path tempDir) throws IOException {
        // Given
        Path nestedDir = tempDir.resolve("system1").resolve("docs");
        Files.createDirectories(nestedDir);
        Path treeFile = nestedDir.resolve("tree.json");
        String jsonContent = "{\"docName\":\"nested\"}";
        Files.writeString(treeFile, jsonContent, StandardCharsets.UTF_8);

        DocumentTree expectedTree = new DocumentTree();
        expectedTree.setDocName("nested");
        expectedTree.setStructure(List.of());

        when(jsonUtils.fromJson(jsonContent, DocumentTree.class)).thenReturn(expectedTree);
        when(treeNodeCounter.count(expectedTree.getStructure())).thenReturn(0);

        // When
        DocumentTree result = treeFileLoader.load(treeFile);

        // Then
        assertThat(result.getDocName()).isEqualTo("nested");
    }

    @Test
    void testLoadWithChineseContent(@TempDir Path tempDir) throws IOException {
        // Given
        Path treeFile = tempDir.resolve("tree.json");
        String jsonContent = "{\"docName\":\"中文文档\",\"docDescription\":\"这是一个测试文档\"}";
        Files.writeString(treeFile, jsonContent, StandardCharsets.UTF_8);

        DocumentTree expectedTree = new DocumentTree();
        expectedTree.setDocName("中文文档");
        expectedTree.setDocDescription("这是一个测试文档");

        when(jsonUtils.fromJson(jsonContent, DocumentTree.class)).thenReturn(expectedTree);
        when(treeNodeCounter.count(expectedTree.getStructure())).thenReturn(0);

        // When
        DocumentTree result = treeFileLoader.load(treeFile);

        // Then
        assertThat(result.getDocName()).isEqualTo("中文文档");
        assertThat(result.getDocDescription()).isEqualTo("这是一个测试文档");
    }

    @Test
    void testLoadEmptyFile(@TempDir Path tempDir) throws IOException {
        // Given
        Path treeFile = tempDir.resolve("empty.json");
        Files.createFile(treeFile);

        DocumentTree expectedTree = new DocumentTree();
        when(jsonUtils.fromJson("", DocumentTree.class)).thenReturn(expectedTree);
        when(treeNodeCounter.count(expectedTree.getStructure())).thenReturn(0);

        // When
        DocumentTree result = treeFileLoader.load(treeFile);

        // Then
        assertThat(result).isNotNull();
    }
}
