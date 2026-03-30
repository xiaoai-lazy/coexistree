package io.github.xiaoailazy.coexistree.document.service;

import io.github.xiaoailazy.coexistree.shared.enums.ErrorCode;
import io.github.xiaoailazy.coexistree.shared.exception.BusinessException;
import io.github.xiaoailazy.coexistree.shared.util.FilePathUtils;
import io.github.xiaoailazy.coexistree.config.AppStorageProperties;
import io.github.xiaoailazy.coexistree.document.dto.DocumentResponse;
import io.github.xiaoailazy.coexistree.document.entity.DocumentEntity;
import io.github.xiaoailazy.coexistree.document.repository.DocumentRepository;
import io.github.xiaoailazy.coexistree.document.repository.DocumentTreeRepository;
import io.github.xiaoailazy.coexistree.document.storage.MarkdownFileStorageService;
import io.github.xiaoailazy.coexistree.document.event.DocumentUploadedEvent;
import io.github.xiaoailazy.coexistree.knowledge.entity.SystemKnowledgeTreeEntity;
import io.github.xiaoailazy.coexistree.knowledge.repository.SystemKnowledgeTreeRepository;
import io.github.xiaoailazy.coexistree.system.entity.SystemEntity;
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
    private final ApplicationEventPublisher eventPublisher;

    public DocumentServiceImpl(
            DocumentRepository documentRepository,
            DocumentTreeRepository documentTreeRepository,
            SystemService systemService,
            AppStorageProperties storageProperties,
            MarkdownFileStorageService markdownFileStorageService,
            SystemKnowledgeTreeRepository systemKnowledgeTreeRepository,
            ApplicationEventPublisher eventPublisher
    ) {
        this.documentRepository = documentRepository;
        this.documentTreeRepository = documentTreeRepository;
        this.systemService = systemService;
        this.storageProperties = storageProperties;
        this.markdownFileStorageService = markdownFileStorageService;
        this.systemKnowledgeTreeRepository = systemKnowledgeTreeRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public DocumentResponse upload(Long systemId, MultipartFile file) {
        log.info("开始上传文档, systemId={}, fileName={}, size={}",
                systemId, file.getOriginalFilename(), file.getSize());

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
    public List<DocumentResponse> list(Long systemId) {
        log.debug("查询文档列表, systemId={}", systemId);
        if (systemId == null) {
            return documentRepository.findAll().stream()
                    .map(this::toResponse)
                    .toList();
        }
        return documentRepository.findBySystemId(systemId).stream()
                .map(this::toResponse)
                .toList();
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

    private DocumentResponse toResponse(DocumentEntity entity) {
        return new DocumentResponse(
                entity.getId(),
                entity.getSystemId(),
                entity.getDocName(),
                entity.getOriginalFileName(),
                entity.getParseStatus(),
                entity.getParseError(),
                entity.getCreatedAt()
        );
    }
}
