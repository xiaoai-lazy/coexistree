package io.github.xiaoailazy.coexistree.document.service;

import io.github.xiaoailazy.coexistree.shared.enums.ErrorCode;
import io.github.xiaoailazy.coexistree.shared.exception.BusinessException;
import io.github.xiaoailazy.coexistree.document.entity.DocumentTreeEntity;
import io.github.xiaoailazy.coexistree.document.repository.DocumentTreeRepository;
import io.github.xiaoailazy.coexistree.indexer.model.DocumentTree;
import io.github.xiaoailazy.coexistree.indexer.model.TreeNode;
import io.github.xiaoailazy.coexistree.indexer.storage.TreeFileLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentTreeServiceImplTest {

    @Mock
    private DocumentTreeRepository documentTreeRepository;
    @Mock
    private TreeFileLoader treeFileLoader;

    private DocumentTreeServiceImpl documentTreeService;

    @BeforeEach
    void setUp() {
        documentTreeService = new DocumentTreeServiceImpl(documentTreeRepository, treeFileLoader);
    }

    @Test
    void testGetNodeTextSuccess() {
        // Given
        Long documentId = 1L;
        String nodeId = "1.1";
        String treeFilePath = "/data/trees/test/tree.json";

        DocumentTreeEntity treeEntity = new DocumentTreeEntity();
        treeEntity.setDocumentId(documentId);
        treeEntity.setTreeFilePath(treeFilePath);

        TreeNode targetNode = createTreeNode("1.1", "标题", "目标文本内容");
        TreeNode rootNode = createTreeNode("1", "根标题", "根内容", List.of(targetNode));
        DocumentTree documentTree = new DocumentTree();
        documentTree.setStructure(List.of(rootNode));

        when(documentTreeRepository.findByDocumentId(documentId)).thenReturn(Optional.of(treeEntity));
        when(treeFileLoader.load(Path.of(treeFilePath))).thenReturn(documentTree);

        // When
        String result = documentTreeService.getNodeText(documentId, nodeId);

        // Then
        assertThat(result).isEqualTo("目标文本内容");
    }

    @Test
    void testGetNodeTextDocumentNotFound() {
        // Given
        Long documentId = 999L;
        String nodeId = "1.1";

        when(documentTreeRepository.findByDocumentId(documentId)).thenReturn(Optional.empty());

        // Then
        assertThatThrownBy(() -> documentTreeService.getNodeText(documentId, nodeId))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(ErrorCode.TREE_FILE_NOT_FOUND);
                })
                .hasMessageContaining("Document tree not found");
    }

    @Test
    void testGetNodeTextNodeNotFound() {
        // Given
        Long documentId = 1L;
        String nodeId = "999";
        String treeFilePath = "/data/trees/test/tree.json";

        DocumentTreeEntity treeEntity = new DocumentTreeEntity();
        treeEntity.setDocumentId(documentId);
        treeEntity.setTreeFilePath(treeFilePath);

        TreeNode rootNode = createTreeNode("1", "根标题", "根内容");
        DocumentTree documentTree = new DocumentTree();
        documentTree.setStructure(List.of(rootNode));

        when(documentTreeRepository.findByDocumentId(documentId)).thenReturn(Optional.of(treeEntity));
        when(treeFileLoader.load(Path.of(treeFilePath))).thenReturn(documentTree);

        // When
        String result = documentTreeService.getNodeText(documentId, nodeId);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void testGetNodeTextNullText() {
        // Given
        Long documentId = 1L;
        String nodeId = "1";
        String treeFilePath = "/data/trees/test/tree.json";

        DocumentTreeEntity treeEntity = new DocumentTreeEntity();
        treeEntity.setDocumentId(documentId);
        treeEntity.setTreeFilePath(treeFilePath);

        TreeNode node = createTreeNode("1", "标题", null);
        DocumentTree documentTree = new DocumentTree();
        documentTree.setStructure(List.of(node));

        when(documentTreeRepository.findByDocumentId(documentId)).thenReturn(Optional.of(treeEntity));
        when(treeFileLoader.load(Path.of(treeFilePath))).thenReturn(documentTree);

        // When
        String result = documentTreeService.getNodeText(documentId, nodeId);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void testGetNodeTextDeepNested() {
        // Given
        Long documentId = 1L;
        String nodeId = "1.1.2";
        String treeFilePath = "/data/trees/test/tree.json";

        DocumentTreeEntity treeEntity = new DocumentTreeEntity();
        treeEntity.setDocumentId(documentId);
        treeEntity.setTreeFilePath(treeFilePath);

        TreeNode level3Node = createTreeNode("1.1.2", "三级标题", "三级内容");
        TreeNode level2Node = createTreeNode("1.1", "二级标题", "二级内容", List.of(level3Node));
        TreeNode level1Node = createTreeNode("1", "一级标题", "一级内容", List.of(level2Node));

        DocumentTree documentTree = new DocumentTree();
        documentTree.setStructure(List.of(level1Node));

        when(documentTreeRepository.findByDocumentId(documentId)).thenReturn(Optional.of(treeEntity));
        when(treeFileLoader.load(Path.of(treeFilePath))).thenReturn(documentTree);

        // When
        String result = documentTreeService.getNodeText(documentId, nodeId);

        // Then
        assertThat(result).isEqualTo("三级内容");
    }

    @Test
    void testGetNodeTextMultipleRoots() {
        // Given
        Long documentId = 1L;
        String nodeId = "2.1";
        String treeFilePath = "/data/trees/test/tree.json";

        DocumentTreeEntity treeEntity = new DocumentTreeEntity();
        treeEntity.setDocumentId(documentId);
        treeEntity.setTreeFilePath(treeFilePath);

        TreeNode childNode = createTreeNode("2.1", "子节点", "子节点内容");
        TreeNode root1 = createTreeNode("1", "根1", "根1内容");
        TreeNode root2 = createTreeNode("2", "根2", "根2内容", List.of(childNode));

        DocumentTree documentTree = new DocumentTree();
        documentTree.setStructure(List.of(root1, root2));

        when(documentTreeRepository.findByDocumentId(documentId)).thenReturn(Optional.of(treeEntity));
        when(treeFileLoader.load(Path.of(treeFilePath))).thenReturn(documentTree);

        // When
        String result = documentTreeService.getNodeText(documentId, nodeId);

        // Then
        assertThat(result).isEqualTo("子节点内容");
    }

    @Test
    void testGetNodeTextEmptyStructure() {
        // Given
        Long documentId = 1L;
        String nodeId = "1";
        String treeFilePath = "/data/trees/test/tree.json";

        DocumentTreeEntity treeEntity = new DocumentTreeEntity();
        treeEntity.setDocumentId(documentId);
        treeEntity.setTreeFilePath(treeFilePath);

        DocumentTree documentTree = new DocumentTree();
        documentTree.setStructure(List.of());

        when(documentTreeRepository.findByDocumentId(documentId)).thenReturn(Optional.of(treeEntity));
        when(treeFileLoader.load(Path.of(treeFilePath))).thenReturn(documentTree);

        // When
        String result = documentTreeService.getNodeText(documentId, nodeId);

        // Then
        assertThat(result).isEmpty();
    }

    private TreeNode createTreeNode(String nodeId, String title, String text) {
        TreeNode node = new TreeNode();
        node.setNodeId(nodeId);
        node.setTitle(title);
        node.setText(text);
        node.setLevel(nodeId.split("\\.").length);
        return node;
    }

    private TreeNode createTreeNode(String nodeId, String title, String text, List<TreeNode> children) {
        TreeNode node = createTreeNode(nodeId, title, text);
        node.setNodes(children);
        return node;
    }
}
