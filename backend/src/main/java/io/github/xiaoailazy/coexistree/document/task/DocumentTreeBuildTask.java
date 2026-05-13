package io.github.xiaoailazy.coexistree.document.task;

import io.github.xiaoailazy.coexistree.shared.entity.DocProcessLogEntity;
import io.github.xiaoailazy.coexistree.shared.enums.ErrorCode;
import io.github.xiaoailazy.coexistree.shared.exception.BusinessException;
import io.github.xiaoailazy.coexistree.shared.repository.DocProcessLogRepository;
import io.github.xiaoailazy.coexistree.shared.util.JsonUtils;
import io.github.xiaoailazy.coexistree.document.entity.DocumentEntity;
import io.github.xiaoailazy.coexistree.document.entity.DocumentTreeEntity;
import io.github.xiaoailazy.coexistree.document.repository.DocumentRepository;
import io.github.xiaoailazy.coexistree.document.repository.DocumentTreeRepository;
import io.github.xiaoailazy.coexistree.knowledge.service.SystemKnowledgeTreeService;
import io.github.xiaoailazy.coexistree.indexer.facade.PageIndexMarkdownService;
import io.github.xiaoailazy.coexistree.indexer.model.DocumentTree;
import io.github.xiaoailazy.coexistree.indexer.model.PageIndexBuildOptions;
import io.github.xiaoailazy.coexistree.indexer.tree.TreeNodeCounter;
import io.github.xiaoailazy.coexistree.system.entity.SystemEntity;
import io.github.xiaoailazy.coexistree.system.service.SystemService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Component
public class DocumentTreeBuildTask {

    private final DocumentRepository documentRepository;
    private final DocumentTreeRepository documentTreeRepository;
    private final DocProcessLogRepository processLogRepository;
    private final SystemService systemService;
    private final SystemKnowledgeTreeService systemKnowledgeTreeService;
    private final PageIndexMarkdownService pageIndexMarkdownService;
    private final JsonUtils jsonUtils;
    private final TreeNodeCounter treeNodeCounter;

    public DocumentTreeBuildTask(
            DocumentRepository documentRepository,
            DocumentTreeRepository documentTreeRepository,
            DocProcessLogRepository processLogRepository,
            SystemService systemService,
            SystemKnowledgeTreeService systemKnowledgeTreeService,
            PageIndexMarkdownService pageIndexMarkdownService,
            JsonUtils jsonUtils,
            TreeNodeCounter treeNodeCounter
    ) {
        this.documentRepository = documentRepository;
        this.documentTreeRepository = documentTreeRepository;
        this.processLogRepository = processLogRepository;
        this.systemService = systemService;
        this.systemKnowledgeTreeService = systemKnowledgeTreeService;
        this.pageIndexMarkdownService = pageIndexMarkdownService;
        this.jsonUtils = jsonUtils;
        this.treeNodeCounter = treeNodeCounter;
    }

    @Transactional
    public void submit(Long documentId) {
        log.info("开始处理文档树生成任务, documentId={}", documentId);

        DocumentEntity document = documentRepository.findById(documentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND, "Document not found"));
        SystemEntity system = systemService.getEntity(document.getSystemId());

        markDocumentProcessing(document);

        try {
            buildTree(document, system);
            log.info("文档树生成成功, documentId={}", documentId);
        } catch (Exception ex) {
            log.error("文档树生成失败, documentId={}", documentId, ex);
            handleBuildFailure(document, system, ex);
        }
    }

    private void markDocumentProcessing(DocumentEntity document) {
        log.debug("标记文档为处理中, documentId={}", document.getId());
        document.setParseStatus("PROCESSING");
        document.setParseError(null);
        document.setUpdatedAt(LocalDateTime.now());
        documentRepository.save(document);
        logProcess(document.getId(), "TREE_BUILD", "PROCESSING", "开始生成树结构");
    }

    private void buildTree(DocumentEntity document, SystemEntity system) {
        log.debug("开始构建树结构, documentId={}", document.getId());

        DocumentTree tree = pageIndexMarkdownService.buildTree(
                document.getFileContent(),
                document.getDocName(),
                PageIndexBuildOptions.defaultOptions(null)
        );

        int nodeCount = treeNodeCounter.count(tree.getStructure());
        log.debug("树结构构建完成, 节点数量={}", nodeCount);

        DocumentTreeEntity treeEntity = documentTreeRepository.findByDocumentId(document.getId())
                .orElseGet(DocumentTreeEntity::new);

        LocalDateTime now = LocalDateTime.now();
        if (treeEntity.getId() == null) {
            treeEntity.setDocumentId(document.getId());
            treeEntity.setCreatedAt(now);
        }
        treeEntity.setTreeJson(jsonUtils.toPrettyJson(tree));
        treeEntity.setDocDescription(tree.getDocDescription());
        treeEntity.setNodeCount(nodeCount);
        treeEntity.setUpdatedAt(now);
        documentTreeRepository.save(treeEntity);

        log.debug("文档树记录保存成功, treeId={}", treeEntity.getId());

        document.setParseStatus("SUCCESS");
        document.setParseError(null);
        document.setUpdatedAt(now);
        documentRepository.save(document);

        logProcess(document.getId(), "TREE_BUILD", "SUCCESS", "树结构生成成功, 节点数量: " + nodeCount);

        // 13.1.2 根据 document.getDocType() 触发后续任务
        String docType = document.getDocType();
        log.info("文档类型={}, docType值={}, 开始触发合并任务", docType, docType);

        if ("BASELINE".equals(docType)) {
            // 13.1.2.1 BASELINE → 调用 systemKnowledgeTreeService.mergeBaseline()
            log.info("触发基线合并, documentId={}", document.getId());
            systemKnowledgeTreeService.mergeBaseline(document.getId(), tree, system);
        } else if ("CHANGE".equals(docType)) {
            // 13.1.2.2 CHANGE → 调用 systemKnowledgeTreeService.mergeChange()
            log.info("触发变更合并, documentId={}", document.getId());
            systemKnowledgeTreeService.mergeChange(document.getId(), tree, system);
        } else {
            log.warn("未知的文档类型: {}, 跳过合并任务", docType);
        }
    }

    private void handleBuildFailure(DocumentEntity document, SystemEntity system, Exception ex) {
        log.error("树构建失败, documentId={}", document.getId(), ex);
        LocalDateTime now = LocalDateTime.now();

        document.setParseStatus("FAILED");
        document.setParseError(ex.getMessage());
        document.setUpdatedAt(now);
        documentRepository.save(document);

        DocumentTreeEntity treeEntity = documentTreeRepository.findByDocumentId(document.getId())
                .orElseGet(DocumentTreeEntity::new);
        if (treeEntity.getId() == null) {
            treeEntity.setDocumentId(document.getId());
            treeEntity.setCreatedAt(now);
        }
        treeEntity.setTreeJson("{}");
        treeEntity.setNodeCount(0);
        treeEntity.setUpdatedAt(now);
        documentTreeRepository.save(treeEntity);

        logProcess(document.getId(), "TREE_BUILD", "FAILED", ex.getMessage());
    }

    private void logProcess(Long documentId, String processStage, String processStatus, String message) {
        DocProcessLogEntity logEntity = new DocProcessLogEntity();
        logEntity.setEntityType("DOCUMENT");
        logEntity.setEntityId(documentId);
        logEntity.setProcessStage(processStage);
        logEntity.setProcessStatus(processStatus);
        logEntity.setMessage(message);
        logEntity.setCreatedAt(LocalDateTime.now());
        processLogRepository.save(logEntity);
        log.debug("处理日志记录成功, documentId={}, stage={}, status={}", documentId, processStage, processStatus);
    }
}
