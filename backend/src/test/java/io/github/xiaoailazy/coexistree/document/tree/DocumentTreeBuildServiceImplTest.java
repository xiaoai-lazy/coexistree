package io.github.xiaoailazy.coexistree.document.tree;

import io.github.xiaoailazy.coexistree.document.entity.DocumentEntity;
import io.github.xiaoailazy.coexistree.document.repository.DocumentRepository;
import io.github.xiaoailazy.coexistree.document.repository.DocumentTreeRepository;
import io.github.xiaoailazy.coexistree.indexer.facade.PageIndexMarkdownService;
import io.github.xiaoailazy.coexistree.indexer.model.DocumentTree;
import io.github.xiaoailazy.coexistree.indexer.model.PageIndexBuildOptions;
import io.github.xiaoailazy.coexistree.indexer.tree.TreeNodeCounter;
import io.github.xiaoailazy.coexistree.shared.util.JsonUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentTreeBuildServiceImplTest {

    @Mock private DocumentRepository documentRepository;
    @Mock private DocumentTreeRepository documentTreeRepository;
    @Mock private PageIndexMarkdownService pageIndexMarkdownService;
    @Mock private JsonUtils jsonUtils;
    @Mock private TreeNodeCounter treeNodeCounter;

    @InjectMocks private DocumentTreeBuildServiceImpl service;

    @Test
    void skipsTaskList() {
        when(documentRepository.findAllByChangeRecordIdOrderByIdAsc(10L))
                .thenReturn(List.of(taskListDoc(), designDoc()));

        DocumentTree built = new DocumentTree();
        built.setDocName("design.md");
        built.setStructure(new ArrayList<>());
        when(pageIndexMarkdownService.buildTree(anyString(), eq("design.md"), any(PageIndexBuildOptions.class)))
                .thenReturn(built);
        when(treeNodeCounter.count(any())).thenReturn(3);
        when(documentTreeRepository.findByDocumentId(201L)).thenReturn(Optional.empty());
        when(jsonUtils.toPrettyJson(built)).thenReturn("{}");

        service.buildDocumentTreesForChange(10L);

        verify(pageIndexMarkdownService, times(1)).buildTree(anyString(), anyString(), any());
    }

    @Test
    void marksTaskListSkipped() {
        DocumentEntity task = taskListDoc();
        when(documentRepository.findAllByChangeRecordIdOrderByIdAsc(10L)).thenReturn(List.of(task));

        service.buildDocumentTreesForChange(10L);

        verify(pageIndexMarkdownService, times(0)).buildTree(anyString(), anyString(), any());
        verify(documentRepository).save(any());
        assertThat(task.getTreeBuildStatus()).isEqualTo(DocumentTreeBuildServiceImpl.STATUS_SKIPPED);
        assertThat(task.getMergeStatus()).isEqualTo(DocumentTreeBuildServiceImpl.STATUS_SKIPPED);
    }

    @Test
    void requirementTriggersBuildAndSuccessStatus() {
        DocumentEntity req = requirementDoc();
        when(documentRepository.findAllByChangeRecordIdOrderByIdAsc(7L)).thenReturn(List.of(req));

        DocumentTree built = new DocumentTree();
        built.setDocName("req.md");
        built.setStructure(new ArrayList<>());
        when(pageIndexMarkdownService.buildTree(anyString(), eq("req.md"), any(PageIndexBuildOptions.class)))
                .thenReturn(built);
        when(treeNodeCounter.count(any())).thenReturn(1);
        when(documentTreeRepository.findByDocumentId(301L)).thenReturn(Optional.empty());
        when(jsonUtils.toPrettyJson(built)).thenReturn("{}");

        service.buildDocumentTreesForChange(7L);

        verify(pageIndexMarkdownService, times(1)).buildTree(anyString(), anyString(), any());
        assertThat(req.getTreeBuildStatus()).isEqualTo(DocumentTreeBuildServiceImpl.STATUS_SUCCESS);
    }

    private static DocumentEntity taskListDoc() {
        DocumentEntity d = baseDoc(200L, 10L);
        d.setDocContentType(DocumentTreeBuildServiceImpl.DOC_TASK_LIST);
        d.setDocName("tasks.md");
        return d;
    }

    private static DocumentEntity designDoc() {
        DocumentEntity d = baseDoc(201L, 10L);
        d.setDocContentType(DocumentTreeBuildServiceImpl.DOC_DESIGN);
        d.setDocName("design.md");
        return d;
    }

    private static DocumentEntity requirementDoc() {
        DocumentEntity d = baseDoc(301L, 7L);
        d.setDocContentType(DocumentTreeBuildServiceImpl.DOC_REQUIREMENT);
        d.setDocName("req.md");
        return d;
    }

    private static DocumentEntity baseDoc(long id, long changeRecordId) {
        DocumentEntity d = new DocumentEntity();
        d.setId(id);
        d.setSystemId(1L);
        d.setChangeRecordId(changeRecordId);
        d.setFileContent("# Title\n");
        d.setMarkdownContent(null);
        return d;
    }
}
