package io.github.xiaoailazy.coexistree.document.service;

import io.github.xiaoailazy.coexistree.document.dto.DocumentContentResponse;
import io.github.xiaoailazy.coexistree.document.dto.DocumentResponse;
import io.github.xiaoailazy.coexistree.document.dto.NodeAnchor;
import io.github.xiaoailazy.coexistree.document.entity.DocumentEntity;
import io.github.xiaoailazy.coexistree.document.entity.DocumentTreeEntity;
import io.github.xiaoailazy.coexistree.document.repository.DocumentRepository;
import io.github.xiaoailazy.coexistree.indexer.model.DocumentTree;
import io.github.xiaoailazy.coexistree.indexer.model.TreeNode;
import io.github.xiaoailazy.coexistree.indexer.storage.TreeFileLoader;
import io.github.xiaoailazy.coexistree.security.model.SecurityUserDetails;
import io.github.xiaoailazy.coexistree.shared.enums.ErrorCode;
import io.github.xiaoailazy.coexistree.shared.exception.BusinessException;
import io.github.xiaoailazy.coexistree.shared.util.FilePathUtils;
import io.github.xiaoailazy.coexistree.config.AppStorageProperties;
import io.github.xiaoailazy.coexistree.document.repository.DocumentTreeRepository;
import io.github.xiaoailazy.coexistree.document.storage.MarkdownFileStorageService;
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

import java.nio.file.Files;
import java.nio.file.Path;
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
    private final AppStorageProperties storageProperties;
    private final MarkdownFileStorageService markdownFileStorageService;
    private final SystemKnowledgeTreeRepository systemKnowledgeTreeRepository;
    private final SystemUserMappingRepository systemUserMappingRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final TreeFileLoader treeFileLoader;

    public DocumentServiceImpl(
            DocumentRepository documentRepository,
            DocumentTreeRepository documentTreeRepository,
            SystemService systemService,
            AppStorageProperties storageProperties,
            MarkdownFileStorageService markdownFileStorageService,
            SystemKnowledgeTreeRepository systemKnowledgeTreeRepository,
            SystemUserMappingRepository systemUserMappingRepository,
            ApplicationEventPublisher eventPublisher,
            TreeFileLoader treeFileLoader
    ) {
        this.documentRepository = documentRepository;
        this.documentTreeRepository = documentTreeRepository;
        this.systemService = systemService;
        this.storageProperties = storageProperties;
        this.markdownFileStorageService = markdownFileStorageService;
        this.systemKnowledgeTreeRepository = systemKnowledgeTreeRepository;
        this.systemUserMappingRepository = systemUserMappingRepository;
        this.eventPublisher = eventPublisher;
        this.treeFileLoader = treeFileLoader;
    }

    @Override
    @Transactional
    public DocumentResponse upload(MultipartFile file, Long systemId, Integer securityLevel, SecurityUserDetails userDetails) {
        log.info("开始上传文档, systemId={}, fileName={}, size={}",
                systemId, file.getOriginalFilename(), file.getSize());

        // Check permission
        checkUploadPermission(systemId, userDetails, securityLevel);

        validateMarkdown(file);
        SystemEntity system = systemService.getEntity(systemId);

        // 计算文件内容哈希，用于防重复上传
        String contentHash = calculateContentHash(file);

        // 检查是否已存在相同内容的文档（同一系统内）
        if (contentHash != null && isDuplicateDocument(systemId, contentHash)) {
            log.warn("检测到重复上传, systemId={}, fileName={}", systemId, file.getOriginalFilename());
            throw new BusinessException(ErrorCode.DUPLICATE_DOCUMENT,
                "该文档已存在，请勿重复上传");
        }

        // 使用悲观锁查询系统树状态，防止并发创建冲突
        Optional<SystemKnowledgeTreeEntity> treeOpt =
            systemKnowledgeTreeRepository.findBySystemIdWithLock(systemId);
        String docType = determineDocType(treeOpt);
        log.debug("自动判断文档类型, systemId={}, docType={}", systemId, docType);

        DocumentEntity entity = new DocumentEntity();
        entity.setSystemId(systemId);
        entity.setDocName(file.getOriginalFilename());
        entity.setOriginalFileName(file.getOriginalFilename());
        entity.setContentType("markdown");
        entity.setParseStatus("PENDING");
        entity.setDocType(docType);
        entity.setFilePath("");
        entity.setContentHash(contentHash);  // 保存哈希值
        entity.setSecurityLevel(securityLevel != null ? securityLevel : 1);
        entity.setUploadedBy(userDetails.getId());
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());

        try {
            DocumentEntity saved = documentRepository.save(entity);
            log.debug("文档元数据保存成功, documentId={}, docType={}", saved.getId(), saved.getDocType());

            Path path = FilePathUtils.markdownPath(
                    storageProperties.docRoot(), system.getSystemCode(), saved.getId());
            log.debug("文档存储路径, path={}", path);

            markdownFileStorageService.save(path, file);
            saved.setFilePath(path.toString());
            saved.setUpdatedAt(LocalDateTime.now());
            saved = documentRepository.save(saved);

            log.info("文档保存成功, documentId={}, path={}, docType={}", saved.getId(), path, saved.getDocType());

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
        checkSystemAccess(systemId, userDetails);

        List<DocumentEntity> documents = documentRepository.findBySystemId(systemId);

        // Filter by view level for non-owners
        Integer viewLevel = getViewLevel(systemId, userDetails);
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
                    deleteTreeFile(tree.getTreeFilePath());
                    documentTreeRepository.delete(tree);
                    log.debug("删除文档树记录, treeId={}", tree.getId());
                });

        deleteMarkdownFile(document.getFilePath());

        documentRepository.delete(document);
        log.info("文档删除成功, documentId={}", documentId);
    }

    private void deleteMarkdownFile(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return;
        }
        try {
            Path path = Path.of(filePath);
            if (Files.exists(path)) {
                Files.delete(path);
                log.debug("删除Markdown文件成功, path={}", path);
            }
        } catch (Exception e) {
            log.warn("删除Markdown文件失败, path={}", filePath, e);
        }
    }

    private void deleteTreeFile(String treeFilePath) {
        if (treeFilePath == null || treeFilePath.isBlank()) {
            return;
        }
        try {
            Path path = Path.of(treeFilePath);
            if (Files.exists(path)) {
                Files.delete(path);
                log.debug("删除树文件成功, path={}", path);
            }
        } catch (Exception e) {
            log.warn("删除树文件失败, path={}", treeFilePath, e);
        }
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
        checkDocumentAccess(document, userDetails);

        // 3. 读取原始文件内容
        String content = readOriginalFile(document.getFilePath());

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

    private void checkDocumentAccess(DocumentEntity document, SecurityUserDetails userDetails) {
        // SUPER_ADMIN 有全部权限
        if (userDetails.getRole().name().equals("SUPER_ADMIN")) {
            return;
        }

        // 检查系统访问权限
        systemUserMappingRepository.findBySystemIdAndUserId(document.getSystemId(), userDetails.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PERMISSION_DENIED, "无权限访问此文档"));

        // 检查密级
        Integer viewLevel = getViewLevel(document.getSystemId(), userDetails);
        if (viewLevel != null && document.getSecurityLevel() > viewLevel) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED, "文档密级超出您的查看权限");
        }
    }

    private String readOriginalFile(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return "";
        }
        try {
            Path path = Path.of(filePath);
            if (Files.exists(path)) {
                return Files.readString(path);
            }
            log.warn("文件不存在, path={}", filePath);
            return "";
        } catch (Exception e) {
            log.error("读取文件失败, path={}", filePath, e);
            return "";
        }
    }

    private List<NodeAnchor> getDocumentAnchors(Long documentId) {
        Optional<DocumentTreeEntity> treeOpt = documentTreeRepository.findByDocumentId(documentId);
        if (treeOpt.isEmpty()) {
            return List.of();
        }

        try {
            DocumentTree tree = treeFileLoader.load(Path.of(treeOpt.get().getTreeFilePath()));
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
            Integer viewLevel = getViewLevel(document.getSystemId(), userDetails);
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

    private void checkSystemAccess(Long systemId, SecurityUserDetails userDetails) {
        if (userDetails.getRole().name().equals("SUPER_ADMIN")) {
            return;
        }

        systemUserMappingRepository.findBySystemIdAndUserId(systemId, userDetails.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PERMISSION_DENIED, "无权限访问此系统"));
    }

    private Integer getViewLevel(Long systemId, SecurityUserDetails userDetails) {
        if (userDetails.getRole().name().equals("SUPER_ADMIN")) {
            return 5;
        }

        return systemUserMappingRepository.findBySystemIdAndUserId(systemId, userDetails.getId())
                .map(SystemUserMappingEntity::getViewLevel)
                .orElse(0);
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
