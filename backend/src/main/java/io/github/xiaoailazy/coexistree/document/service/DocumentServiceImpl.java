package io.github.xiaoailazy.coexistree.document.service;

import io.github.xiaoailazy.coexistree.change.entity.SystemChangeRecordEntity;
import io.github.xiaoailazy.coexistree.change.repository.SystemChangeRecordRepository;
import io.github.xiaoailazy.coexistree.document.dto.ChangeDocumentUploadCommand;
import io.github.xiaoailazy.coexistree.document.dto.DocumentContentResponse;
import io.github.xiaoailazy.coexistree.document.dto.DocumentResponse;
import io.github.xiaoailazy.coexistree.document.dto.NodeAnchor;
import io.github.xiaoailazy.coexistree.document.entity.DocumentEntity;
import io.github.xiaoailazy.coexistree.document.entity.DocumentTreeEntity;
import io.github.xiaoailazy.coexistree.document.repository.DocumentRepository;
import io.github.xiaoailazy.coexistree.indexer.model.DocumentTree;
import io.github.xiaoailazy.coexistree.indexer.model.TreeNode;
import io.github.xiaoailazy.coexistree.security.model.SecurityUserDetails;
import io.github.xiaoailazy.coexistree.shared.enums.ErrorCode;
import io.github.xiaoailazy.coexistree.shared.exception.BusinessException;
import io.github.xiaoailazy.coexistree.shared.util.JsonUtils;
import io.github.xiaoailazy.coexistree.document.repository.DocumentTreeRepository;
import io.github.xiaoailazy.coexistree.document.event.DocumentUploadedEvent;
import io.github.xiaoailazy.coexistree.knowledge.entity.SystemKnowledgeTreeEntity;
import io.github.xiaoailazy.coexistree.knowledge.repository.SystemKnowledgeTreeRepository;
import io.github.xiaoailazy.coexistree.system.entity.RelationType;
import io.github.xiaoailazy.coexistree.system.entity.SystemEntity;
import io.github.xiaoailazy.coexistree.system.entity.SystemUserMappingEntity;
import io.github.xiaoailazy.coexistree.system.repository.SystemUserMappingRepository;
import io.github.xiaoailazy.coexistree.system.service.SystemService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class DocumentServiceImpl implements DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentTreeRepository documentTreeRepository;
    private final SystemService systemService;
    private final SystemKnowledgeTreeRepository systemKnowledgeTreeRepository;
    private final DocumentAccessService documentAccessService;
    private final SystemUserMappingRepository systemUserMappingRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final JsonUtils jsonUtils;
    private final SystemChangeRecordRepository changeRecordRepository;

    public DocumentServiceImpl(
            DocumentRepository documentRepository,
            DocumentTreeRepository documentTreeRepository,
            SystemService systemService,
            SystemKnowledgeTreeRepository systemKnowledgeTreeRepository,
            DocumentAccessService documentAccessService,
            SystemUserMappingRepository systemUserMappingRepository,
            ApplicationEventPublisher eventPublisher,
            JsonUtils jsonUtils,
            SystemChangeRecordRepository changeRecordRepository
    ) {
        this.documentRepository = documentRepository;
        this.documentTreeRepository = documentTreeRepository;
        this.systemService = systemService;
        this.systemKnowledgeTreeRepository = systemKnowledgeTreeRepository;
        this.documentAccessService = documentAccessService;
        this.systemUserMappingRepository = systemUserMappingRepository;
        this.eventPublisher = eventPublisher;
        this.jsonUtils = jsonUtils;
        this.changeRecordRepository = changeRecordRepository;
    }

    @Override
    @Transactional
    public DocumentResponse upload(MultipartFile file, Long systemId, Integer securityLevel, SecurityUserDetails userDetails) {
        log.info("开始上传文档, systemId={}, fileName={}, size={}",
                systemId, file.getOriginalFilename(), file.getSize());

        // Check permission
        checkUploadPermission(systemId, userDetails, securityLevel);

        validateMarkdown(file);

        String fileContent;
        try {
            fileContent = new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new BusinessException(ErrorCode.INVALID_FILE_CONTENT, "Failed to read uploaded file");
        }

        // 计算文件内容哈希，用于防重复上传
        String contentHash = calculateContentHash(file);

        // 检查是否已存在相同内容的文档（同一系统内）
        if (contentHash != null && isDuplicateDocument(systemId, contentHash)) {
            log.warn("检测到重复上传, systemId={}, fileName={}", systemId, file.getOriginalFilename());
            throw new BusinessException(ErrorCode.DUPLICATE_DOCUMENT,
                "该文档已存在，请勿重复上传");
        }

        // 每系统单行：按 ACTIVE → BUILDING → EMPTY 依次加锁，等价于旧版对「当前行」加锁后读取 tree_status
        Optional<SystemKnowledgeTreeEntity> treeOpt = resolveKnowledgeTreeRowWithLock(systemId);
        String docType = determineDocType(treeOpt);
        log.debug("自动判断文档类型, systemId={}, docType={}", systemId, docType);

        DocumentEntity entity = new DocumentEntity();
        entity.setSystemId(systemId);
        entity.setDocName(file.getOriginalFilename());
        entity.setOriginalFileName(file.getOriginalFilename());
        entity.setContentType("markdown");
        entity.setParseStatus("PENDING");
        entity.setDocType(docType);
        entity.setFileContent(fileContent);
        entity.setContentHash(contentHash);  // 保存哈希值
        entity.setSecurityLevel(securityLevel != null ? securityLevel : 1);
        entity.setUploadedBy(userDetails.getId());
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());

        try {
            DocumentEntity saved = documentRepository.save(entity);
            log.info("文档保存成功, documentId={}, docType={}", saved.getId(), saved.getDocType());

            // 发布文档上传事件，触发异步处理（虚拟线程）
            // 使用 AFTER_COMMIT 确保事务提交后才处理
            eventPublisher.publishEvent(new DocumentUploadedEvent(saved.getId()));
            log.debug("已发布文档上传事件, documentId={}", saved.getId());

            return toResponse(saved);
        } catch (DataIntegrityViolationException e) {
            log.error("文档保存冲突, systemId={}, fileName={}", systemId, file.getOriginalFilename(), e);
            throw new BusinessException(ErrorCode.DUPLICATE_DOCUMENT,
                "文档正在处理中，请勿重复提交");
        }
    }

    @Override
    @Transactional
    public DocumentResponse uploadForChange(
            Long systemId,
            Long changeRecordId,
            ChangeDocumentUploadCommand command,
            SecurityUserDetails userDetails) {
        log.info(
                "变更批次上传文档, systemId={}, changeRecordId={}, fileName={}",
                systemId,
                changeRecordId,
                command.originalFileName());

        Integer securityLevel = command.securityLevel() != null ? command.securityLevel() : 1;
        checkUploadPermission(systemId, userDetails, securityLevel);
        validateChangeUpload(command);

        SystemChangeRecordEntity locked =
                changeRecordRepository
                        .findByIdForUpdate(changeRecordId)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                ErrorCode.APPLY_PRECONDITION_FAILED,
                                                "变更记录不存在"));

        if (!locked.getSystemId().equals(systemId)) {
            throw new BusinessException(
                    ErrorCode.APPLY_PRECONDITION_FAILED, "变更记录不属于当前系统");
        }
        if (locked.getTreeVersionAfter() != null) {
            throw new BusinessException(
                    ErrorCode.APPLY_PRECONDITION_FAILED, "变更批次已应用，无法再上传文档");
        }

        String markdown = command.markdownContent();
        String contentHash = calculateContentHashFromUtf8(markdown);
        if (contentHash != null && isDuplicateDocument(systemId, contentHash)) {
            log.warn(
                    "检测到重复上传, systemId={}, changeRecordId={}, fileName={}",
                    systemId,
                    changeRecordId,
                    command.originalFileName());
            throw new BusinessException(ErrorCode.DUPLICATE_DOCUMENT, "该文档已存在，请勿重复上传");
        }

        DocumentEntity entity = new DocumentEntity();
        entity.setSystemId(systemId);
        entity.setChangeRecordId(changeRecordId);
        entity.setDocName(command.originalFileName());
        entity.setOriginalFileName(command.originalFileName());
        entity.setContentType("markdown");
        entity.setParseStatus("PENDING");
        entity.setDocType(legacyDocTypeForDocContentType(command.docContentType()));
        entity.setDocContentType(command.docContentType());
        entity.setMarkdownContent(markdown);
        entity.setFileContent(markdown);
        entity.setContentHash(contentHash);
        entity.setTreeBuildStatus("PENDING");
        entity.setMergeStatus("PENDING");
        entity.setSecurityLevel(securityLevel);
        entity.setUploadedBy(userDetails.getId());
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());

        try {
            DocumentEntity saved = documentRepository.save(entity);
            log.info(
                    "变更批次文档保存成功, documentId={}, changeRecordId={}",
                    saved.getId(),
                    changeRecordId);
            return toResponse(saved);
        } catch (DataIntegrityViolationException e) {
            log.error(
                    "文档保存冲突, systemId={}, changeRecordId={}, fileName={}",
                    systemId,
                    changeRecordId,
                    command.originalFileName(),
                    e);
            throw new BusinessException(ErrorCode.DUPLICATE_DOCUMENT, "文档正在处理中，请勿重复提交");
        }
    }

    private static String legacyDocTypeForDocContentType(String docContentType) {
        if ("REQUIREMENT".equals(docContentType)) {
            return "BASELINE";
        }
        if ("DESIGN".equals(docContentType) || "TASK_LIST".equals(docContentType)) {
            return "CHANGE";
        }
        return "BASELINE";
    }

    private void validateChangeUpload(ChangeDocumentUploadCommand command) {
        String name = command.originalFileName();
        if (name == null || (!name.endsWith(".md") && !name.endsWith(".markdown"))) {
            throw new BusinessException(ErrorCode.INVALID_FILE_TYPE, "Only markdown files are supported");
        }
        String body = command.markdownContent();
        if (body == null || body.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_FILE_CONTENT, "Uploaded content is empty");
        }
        if (body.length() > MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCode.FILE_SIZE_EXCEEDED, "File size exceeds 10MB limit");
        }
    }

    private String calculateContentHashFromUtf8(String utf8) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(utf8.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            log.warn("计算内容哈希失败", e);
            return null;
        }
    }

    /**
     * 计算文件内容哈希（MD5）
     */
    private String calculateContentHash(MultipartFile file) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(file.getBytes());
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException | java.io.IOException e) {
            log.warn("计算文件哈希失败, fileName={}", file.getOriginalFilename(), e);
            return null;
        }
    }

    /**
     * 检查是否已存在相同内容的文档
     */
    private boolean isDuplicateDocument(Long systemId, String contentHash) {
        if (contentHash == null) return false;
        return documentRepository.existsBySystemIdAndContentHashAndParseStatusNot(
            systemId, contentHash, "FAILED"
        );
    }

    /**
     * 根据系统知识树状态自动判断文档类型
     * - 系统树不存在或状态为 EMPTY → BASELINE
     * - 系统树状态为 ACTIVE → CHANGE
     * - 系统树状态为 BUILDING → CHANGE（保守策略，避免并发冲突）
     */
    private String determineDocType(Optional<SystemKnowledgeTreeEntity> treeOpt) {
        if (treeOpt.isEmpty()) {
            return "BASELINE";
        }

        String treeStatus = treeOpt.get().getTreeStatus();
        if ("EMPTY".equals(treeStatus)) {
            return "BASELINE";
        }

        return "CHANGE";
    }

    /**
     * 与 {@link #determineDocType} 配套：在单行模型下锁定当前系统树行（状态为三者之一）。
     */
    private Optional<SystemKnowledgeTreeEntity> resolveKnowledgeTreeRowWithLock(Long systemId) {
        Optional<SystemKnowledgeTreeEntity> active =
                systemKnowledgeTreeRepository.findBySystemIdAndTreeStatusWithLock(systemId, "ACTIVE");
        if (active.isPresent()) {
            return active;
        }
        Optional<SystemKnowledgeTreeEntity> building =
                systemKnowledgeTreeRepository.findBySystemIdAndTreeStatusWithLock(systemId, "BUILDING");
        if (building.isPresent()) {
            return building;
        }
        return systemKnowledgeTreeRepository.findBySystemIdAndTreeStatusWithLock(systemId, "EMPTY");
    }

    @Override
    public DocumentResponse getById(Long documentId) {
        log.debug("查询文档, documentId={}", documentId);

        DocumentEntity entity = documentRepository.findById(documentId)
                .orElseThrow(() -> {
                    log.warn("文档不存在, documentId={}", documentId);
                    return new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND, "Document not found");
                });

        return toResponse(entity);
    }

    @Override
    public List<DocumentResponse> listBySystem(Long systemId, SecurityUserDetails userDetails) {
        log.debug("查询文档列表, systemId={}", systemId);

        // Check access
        documentAccessService.checkSystemAccess(systemId, userDetails);

        List<DocumentEntity> documents = documentRepository.findBySystemId(systemId);

        // Filter by view level for non-owners
        Integer viewLevel = documentAccessService.getViewLevel(systemId, userDetails);
        if (viewLevel != null && viewLevel < 5) {
            documents = documents.stream()
                    .filter(d -> d.getSecurityLevel() <= viewLevel)
                    .toList();
        }

        return documents.stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional
    public void delete(Long documentId) {
        log.info("开始删除文档, documentId={}", documentId);

        DocumentEntity document = documentRepository.findById(documentId)
                .orElseThrow(() -> {
                    log.warn("文档不存在, documentId={}", documentId);
                    return new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND, "Document not found");
                });

        documentTreeRepository.findByDocumentId(documentId)
                .ifPresent(tree -> {
                    documentTreeRepository.delete(tree);
                    log.debug("删除文档树记录, treeId={}", tree.getId());
                });

        documentRepository.delete(document);
        log.info("文档删除成功, documentId={}", documentId);
    }

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;

    private void validateMarkdown(MultipartFile file) {
        String name = file.getOriginalFilename();
        if (name == null || (!name.endsWith(".md") && !name.endsWith(".markdown"))) {
            log.warn("文件类型不支持, fileName={}", name);
            throw new BusinessException(ErrorCode.INVALID_FILE_TYPE, "Only markdown files are supported");
        }
        if (file.isEmpty()) {
            log.warn("上传文件为空, fileName={}", name);
            throw new BusinessException(ErrorCode.INVALID_FILE_CONTENT, "Uploaded file is empty");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            log.warn("文件大小超过限制, fileName={}, size={}", name, file.getSize());
            throw new BusinessException(ErrorCode.FILE_SIZE_EXCEEDED, "File size exceeds 10MB limit");
        }
    }

    @Override
    public DocumentContentResponse getContent(Long documentId, SecurityUserDetails userDetails) {
        log.debug("获取文档内容, documentId={}", documentId);

        // 1. 查询文档
        DocumentEntity document = documentRepository.findById(documentId)
                .orElseThrow(() -> {
                    log.warn("文档不存在, documentId={}", documentId);
                    return new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND, "Document not found");
                });

        // 2. 权限校验（复用现有逻辑）
        documentAccessService.requireCanReadDocument(document, userDetails);

        // 3. 读取原始文件内容
        String content = document.getFileContent();

        // 4. 获取文档树锚点
        List<NodeAnchor> anchors = getDocumentAnchors(documentId);

        // 5. 构建响应
        String downloadUrl = "/api/v1/documents/" + documentId + "/download";

        return new DocumentContentResponse(
                document.getId(),
                document.getDocName(),
                "text/markdown",
                content,
                downloadUrl,
                anchors
        );
    }

    private List<NodeAnchor> getDocumentAnchors(Long documentId) {
        Optional<DocumentTreeEntity> treeOpt = documentTreeRepository.findByDocumentId(documentId);
        if (treeOpt.isEmpty()) {
            return List.of();
        }

        try {
            DocumentTree tree = jsonUtils.fromJson(treeOpt.get().getTreeJson(), DocumentTree.class);
            return extractAnchors(tree.getStructure());
        } catch (Exception e) {
            log.warn("加载文档树失败, documentId={}", documentId, e);
            return List.of();
        }
    }

    private List<NodeAnchor> extractAnchors(List<TreeNode> nodes) {
        List<NodeAnchor> anchors = new ArrayList<>();
        for (TreeNode node : nodes) {
            extractAnchorsRecursive(node, anchors);
        }
        return anchors;
    }

    private void extractAnchorsRecursive(TreeNode node, List<NodeAnchor> anchors) {
        if (node.getNodeId() != null && node.getLineNum() != null) {
            anchors.add(new NodeAnchor(
                    node.getNodeId(),
                    node.getTitle(),
                    node.getLineNum(),
                    node.getLevel()
            ));
        }
        if (node.getNodes() != null) {
            for (TreeNode child : node.getNodes()) {
                extractAnchorsRecursive(child, anchors);
            }
        }
    }

    @Override
    @Transactional
    public void updateSecurityLevel(Long documentId, Integer securityLevel, SecurityUserDetails userDetails) {
        DocumentEntity document = documentRepository.findById(documentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND, "Document not found"));

        // Only SUPER_ADMIN or uploader can change security level
        if (!userDetails.getRole().name().equals("SUPER_ADMIN") &&
                !document.getUploadedBy().equals(userDetails.getId())) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED, "无权限修改此文档");
        }

        // Check view level constraint for MAINTAINER
        if (!userDetails.getRole().name().equals("SUPER_ADMIN")) {
            Integer viewLevel = documentAccessService.getViewLevel(document.getSystemId(), userDetails);
            if (viewLevel != null && securityLevel > viewLevel) {
                throw new BusinessException(ErrorCode.PERMISSION_DENIED, "安全等级不能超过您的查看等级");
            }
        }

        document.setSecurityLevel(securityLevel);
        document.setUpdatedAt(LocalDateTime.now());
        documentRepository.save(document);
    }

    private void checkUploadPermission(Long systemId, SecurityUserDetails userDetails, Integer securityLevel) {
        if (userDetails.getRole().name().equals("SUPER_ADMIN")) {
            return;
        }

        SystemUserMappingEntity mapping = systemUserMappingRepository.findBySystemIdAndUserId(systemId, userDetails.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PERMISSION_DENIED, "无权限上传文档到此系统"));

        if (mapping.getRelationType() == RelationType.SUBSCRIBER) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED, "订阅者不能上传文档");
        }

        if (mapping.getViewLevel() < securityLevel) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED, "文档安全等级不能超过您的查看等级");
        }
    }

    private DocumentResponse toResponse(DocumentEntity entity) {
        return new DocumentResponse(
                entity.getId(),
                entity.getSystemId(),
                entity.getDocName(),
                entity.getOriginalFileName(),
                entity.getParseStatus(),
                entity.getParseError(),
                entity.getCreatedAt(),
                entity.getSecurityLevel(),
                entity.getUploadedBy()
        );
    }
}
