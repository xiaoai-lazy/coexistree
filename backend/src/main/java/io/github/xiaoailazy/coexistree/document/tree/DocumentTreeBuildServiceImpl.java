package io.github.xiaoailazy.coexistree.document.tree;

import io.github.xiaoailazy.coexistree.document.entity.DocumentEntity;
import io.github.xiaoailazy.coexistree.document.entity.DocumentTreeEntity;
import io.github.xiaoailazy.coexistree.document.repository.DocumentRepository;
import io.github.xiaoailazy.coexistree.document.repository.DocumentTreeRepository;
import io.github.xiaoailazy.coexistree.indexer.facade.PageIndexMarkdownService;
import io.github.xiaoailazy.coexistree.indexer.model.DocumentTree;
import io.github.xiaoailazy.coexistree.indexer.model.PageIndexBuildOptions;
import io.github.xiaoailazy.coexistree.indexer.tree.TreeNodeCounter;
import io.github.xiaoailazy.coexistree.shared.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 对同一 {@code change_record_id} 下文档批量构建文档树：仅 REQUIREMENT / DESIGN 调用 Markdown 解析；
 * TASK_LIST / GENERAL 等标记 {@code tree_build_status=SKIPPED}、{@code merge_status=SKIPPED}（需求 §19）。
 */
@Slf4j
@Service
public class DocumentTreeBuildServiceImpl implements DocumentTreeBuildService {

    public static final String DOC_REQUIREMENT = "REQUIREMENT";
    public static final String DOC_DESIGN = "DESIGN";
    public static final String DOC_TASK_LIST = "TASK_LIST";
    public static final String DOC_GENERAL = "GENERAL";

    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_SKIPPED = "SKIPPED";

    private final DocumentRepository documentRepository;
    private final DocumentTreeRepository documentTreeRepository;
    private final PageIndexMarkdownService pageIndexMarkdownService;
    private final JsonUtils jsonUtils;
    private final TreeNodeCounter treeNodeCounter;

    public DocumentTreeBuildServiceImpl(
            DocumentRepository documentRepository,
            DocumentTreeRepository documentTreeRepository,
            PageIndexMarkdownService pageIndexMarkdownService,
            JsonUtils jsonUtils,
            TreeNodeCounter treeNodeCounter) {
        this.documentRepository = documentRepository;
        this.documentTreeRepository = documentTreeRepository;
        this.pageIndexMarkdownService = pageIndexMarkdownService;
        this.jsonUtils = jsonUtils;
        this.treeNodeCounter = treeNodeCounter;
    }

    @Override
    public void buildDocumentTreesForChange(Long changeRecordId) {
        List<DocumentEntity> documents =
                documentRepository.findAllByChangeRecordIdOrderByIdAsc(changeRecordId);
        for (DocumentEntity document : documents) {
            if (shouldSkipTreeBuild(document)) {
                markSkipped(document);
                continue;
            }
            buildRequirementOrDesignTree(document);
        }
    }

    private boolean shouldSkipTreeBuild(DocumentEntity document) {
        String t = document.getDocContentType();
        if (t == null) {
            return true;
        }
        return DOC_TASK_LIST.equals(t) || DOC_GENERAL.equals(t);
    }

    private void markSkipped(DocumentEntity document) {
        LocalDateTime now = LocalDateTime.now();
        document.setTreeBuildStatus(STATUS_SKIPPED);
        document.setMergeStatus(STATUS_SKIPPED);
        document.setUpdatedAt(now);
        documentRepository.save(document);
        log.debug(
                "Skip document tree build for documentId={}, docContentType={}",
                document.getId(),
                document.getDocContentType());
    }

    private void buildRequirementOrDesignTree(DocumentEntity document) {
        String ct = document.getDocContentType();
        if (!DOC_REQUIREMENT.equals(ct) && !DOC_DESIGN.equals(ct)) {
            markSkipped(document);
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        try {
            String markdown = markdownForBuild(document);
            DocumentTree tree =
                    pageIndexMarkdownService.buildTree(
                            markdown,
                            document.getDocName(),
                            PageIndexBuildOptions.defaultOptions(null));
            int nodeCount = treeNodeCounter.count(tree.getStructure());

            DocumentTreeEntity treeEntity =
                    documentTreeRepository.findByDocumentId(document.getId()).orElseGet(DocumentTreeEntity::new);
            if (treeEntity.getId() == null) {
                treeEntity.setDocumentId(document.getId());
                treeEntity.setCreatedAt(now);
            }
            treeEntity.setSystemId(document.getSystemId());
            treeEntity.setTreeJson(jsonUtils.toPrettyJson(tree));
            treeEntity.setDocDescription(tree.getDocDescription());
            treeEntity.setNodeCount(nodeCount);
            treeEntity.setUpdatedAt(now);
            documentTreeRepository.save(treeEntity);

            document.setTreeBuildStatus(STATUS_SUCCESS);
            document.setUpdatedAt(now);
            documentRepository.save(document);
            log.info(
                    "Document tree built for changeRecord documentId={}, docContentType={}, nodes={}",
                    document.getId(),
                    ct,
                    nodeCount);
        } catch (Exception ex) {
            log.error("Document tree build failed, documentId={}", document.getId(), ex);
            persistFailedTreePlaceholder(document, now);
            document.setTreeBuildStatus(STATUS_FAILED);
            document.setMergeStatus(STATUS_FAILED);
            document.setUpdatedAt(now);
            documentRepository.save(document);
        }
    }

    private static String markdownForBuild(DocumentEntity document) {
        if (document.getMarkdownContent() != null && !document.getMarkdownContent().isBlank()) {
            return document.getMarkdownContent();
        }
        return document.getFileContent();
    }

    private void persistFailedTreePlaceholder(DocumentEntity document, LocalDateTime now) {
        DocumentTreeEntity treeEntity =
                documentTreeRepository.findByDocumentId(document.getId()).orElseGet(DocumentTreeEntity::new);
        if (treeEntity.getId() == null) {
            treeEntity.setDocumentId(document.getId());
            treeEntity.setCreatedAt(now);
        }
        treeEntity.setSystemId(document.getSystemId());
        treeEntity.setTreeJson("{}");
        treeEntity.setNodeCount(0);
        treeEntity.setUpdatedAt(now);
        documentTreeRepository.save(treeEntity);
    }
}
