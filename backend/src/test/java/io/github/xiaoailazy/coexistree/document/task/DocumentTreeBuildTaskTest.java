package io.github.xiaoailazy.coexistree.document.task;

import io.github.xiaoailazy.coexistree.shared.enums.ErrorCode;
import io.github.xiaoailazy.coexistree.shared.exception.BusinessException;
import io.github.xiaoailazy.coexistree.shared.repository.DocProcessLogRepository;
import io.github.xiaoailazy.coexistree.shared.util.JsonUtils;
import io.github.xiaoailazy.coexistree.document.entity.DocumentEntity;
import io.github.xiaoailazy.coexistree.document.entity.DocumentTreeEntity;
import io.github.xiaoailazy.coexistree.document.repository.DocumentRepository;
import io.github.xiaoailazy.coexistree.document.repository.DocumentTreeRepository;
import io.github.xiaoailazy.coexistree.indexer.facade.PageIndexMarkdownService;
import io.github.xiaoailazy.coexistree.indexer.model.DocumentTree;
import io.github.xiaoailazy.coexistree.indexer.model.TreeNode;
import io.github.xiaoailazy.coexistree.indexer.tree.TreeNodeCounter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentTreeBuildTaskTest {

    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private DocumentTreeRepository documentTreeRepository;
    @Mock
    private DocProcessLogRepository processLogRepository;
    @Mock
    private PageIndexMarkdownService pageIndexMarkdownService;
    @Mock
    private JsonUtils jsonUtils;
    @Mock
    private TreeNodeCounter treeNodeCounter;

    private DocumentTreeBuildTask documentTreeBuildTask;

    @BeforeEach
    void setUp() {
        documentTreeBuildTask = new DocumentTreeBuildTask(
                documentRepository,
                documentTreeRepository,
                processLogRepository,
                pageIndexMarkdownService,
                jsonUtils,
                treeNodeCounter
        );
    }

    @Test
    void testSubmitBaselineDocument() {
        // Given
        Long documentId = 1L;
        DocumentEntity document = createDocumentEntity(documentId, 1L, "BASELINE", "/data/test.md");
        DocumentTree tree = createDocumentTree("test", List.of(createTreeNode("1", "标题")));

        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));
        when(pageIndexMarkdownService.buildTree(anyString(), anyString(), any())).thenReturn(tree);
        when(treeNodeCounter.count(tree.getStructure())).thenReturn(1);
        when(jsonUtils.toPrettyJson(tree)).thenReturn("{\"structure\":[]}");
        when(documentTreeRepository.findByDocumentId(documentId)).thenReturn(Optional.empty());
        when(documentTreeRepository.save(any(DocumentTreeEntity.class))).thenAnswer(i -> i.getArgument(0));
        when(documentRepository.save(any(DocumentEntity.class))).thenAnswer(i -> i.getArgument(0));

        // When
        documentTreeBuildTask.submit(documentId);

        // Then
        verify(pageIndexMarkdownService, times(1)).buildTree(anyString(), anyString(), any());
        assertThat(document.getParseStatus()).isEqualTo("SUCCESS");
    }

    @Test
    void testSubmitChangeDocument() {
        // Given
        Long documentId = 2L;
        DocumentEntity document = createDocumentEntity(documentId, 1L, "CHANGE", "/data/change.md");
        DocumentTree tree = createDocumentTree("change", List.of(createTreeNode("1", "变更")));

        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));
        when(pageIndexMarkdownService.buildTree(anyString(), anyString(), any())).thenReturn(tree);
        when(treeNodeCounter.count(tree.getStructure())).thenReturn(1);
        when(jsonUtils.toPrettyJson(tree)).thenReturn("{\"structure\":[]}");
        when(documentTreeRepository.findByDocumentId(documentId)).thenReturn(Optional.empty());
        when(documentTreeRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(documentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // When
        documentTreeBuildTask.submit(documentId);

        // Then
        verify(pageIndexMarkdownService, times(1)).buildTree(anyString(), anyString(), any());
        assertThat(document.getParseStatus()).isEqualTo("SUCCESS");
    }

    @Test
    void testSubmitSkipsTreeBuildWhenChangeRecordPresent() {
        Long documentId = 42L;
        DocumentEntity document = createDocumentEntity(documentId, 1L, "BASELINE", "/data/test.md");
        document.setChangeRecordId(100L);

        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));
        when(documentRepository.save(any(DocumentEntity.class))).thenAnswer(i -> i.getArgument(0));

        documentTreeBuildTask.submit(documentId);

        verify(pageIndexMarkdownService, never()).buildTree(anyString(), anyString(), any());
        assertThat(document.getParseStatus()).isEqualTo("SUCCESS");
    }

    @Test
    void testSubmitUnknownDocType() {
        // Given
        Long documentId = 3L;
        DocumentEntity document = createDocumentEntity(documentId, 1L, "UNKNOWN", "/data/unknown.md");
        DocumentTree tree = createDocumentTree("unknown", List.of());

        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));
        when(pageIndexMarkdownService.buildTree(anyString(), anyString(), any())).thenReturn(tree);
        when(treeNodeCounter.count(tree.getStructure())).thenReturn(0);
        when(jsonUtils.toPrettyJson(tree)).thenReturn("{\"structure\":[]}");
        when(documentTreeRepository.findByDocumentId(documentId)).thenReturn(Optional.empty());
        when(documentTreeRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(documentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // When
        documentTreeBuildTask.submit(documentId);

        // Then
        verify(pageIndexMarkdownService, times(1)).buildTree(anyString(), anyString(), any());
    }

    @Test
    void testSubmitDocumentNotFound() {
        // Given
        Long documentId = 999L;
        when(documentRepository.findById(documentId)).thenReturn(Optional.empty());

        // Then
        assertThatThrownBy(() -> documentTreeBuildTask.submit(documentId))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(ErrorCode.DOCUMENT_NOT_FOUND);
                });
    }

    @Test
    void testSubmitBuildFailure() {
        // Given
        Long documentId = 1L;
        DocumentEntity document = createDocumentEntity(documentId, 1L, "BASELINE", "/data/test.md");

        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));
        when(documentRepository.save(any(DocumentEntity.class))).thenAnswer(i -> i.getArgument(0));
        when(pageIndexMarkdownService.buildTree(anyString(), anyString(), any()))
                .thenThrow(new RuntimeException("解析失败"));
        when(documentTreeRepository.findByDocumentId(documentId)).thenReturn(Optional.empty());
        when(documentTreeRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // When
        documentTreeBuildTask.submit(documentId);

        // Then
        ArgumentCaptor<DocumentEntity> captor = ArgumentCaptor.forClass(DocumentEntity.class);
        verify(documentRepository, atLeast(1)).save(captor.capture());
        // Find the last saved document (should be the one with FAILED status)
        DocumentEntity savedDoc = captor.getAllValues().get(captor.getAllValues().size() - 1);
        assertThat(savedDoc.getParseStatus()).isEqualTo("FAILED");
        assertThat(savedDoc.getParseError()).isEqualTo("解析失败");
    }

    @Test
    void testUpdateExistingTreeEntity() {
        // Given
        Long documentId = 1L;
        DocumentEntity document = createDocumentEntity(documentId, 1L, "BASELINE", "/data/test.md");
        DocumentTree tree = createDocumentTree("test", List.of());
        DocumentTreeEntity existingEntity = new DocumentTreeEntity();
        existingEntity.setId(100L);
        existingEntity.setDocumentId(documentId);
        existingEntity.setCreatedAt(LocalDateTime.now().minusDays(1));

        when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));
        when(pageIndexMarkdownService.buildTree(anyString(), anyString(), any())).thenReturn(tree);
        when(treeNodeCounter.count(tree.getStructure())).thenReturn(0);
        when(jsonUtils.toPrettyJson(tree)).thenReturn("{\"structure\":[]}");
        when(documentTreeRepository.findByDocumentId(documentId)).thenReturn(Optional.of(existingEntity));
        when(documentTreeRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(documentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // When
        documentTreeBuildTask.submit(documentId);

        // Then
        ArgumentCaptor<DocumentTreeEntity> captor = ArgumentCaptor.forClass(DocumentTreeEntity.class);
        verify(documentTreeRepository).save(captor.capture());
        DocumentTreeEntity savedEntity = captor.getValue();
        assertThat(savedEntity.getId()).isEqualTo(100L);
        assertThat(savedEntity.getDocumentId()).isEqualTo(documentId);
    }

    private DocumentEntity createDocumentEntity(Long id, Long systemId, String docType, String filePath) {
        DocumentEntity entity = new DocumentEntity();
        entity.setId(id);
        entity.setSystemId(systemId);
        entity.setDocType(docType);
        entity.setDocName("test.md");
        entity.setFileContent("# test");
        entity.setOriginalFileName("test.md");
        return entity;
    }

    private DocumentTree createDocumentTree(String docName, List<TreeNode> structure) {
        DocumentTree tree = new DocumentTree();
        tree.setDocName(docName);
        tree.setDocDescription("测试文档");
        tree.setStructure(structure);
        return tree;
    }

    private TreeNode createTreeNode(String nodeId, String title) {
        TreeNode node = new TreeNode();
        node.setNodeId(nodeId);
        node.setTitle(title);
        node.setLevel(1);
        return node;
    }
}
