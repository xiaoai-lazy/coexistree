package io.github.xiaoailazy.coexistree.document.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "documents")
public class DocumentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "system_id", nullable = false)
    private Long systemId;

    @Column(name = "doc_name", nullable = false, length = 255)
    private String docName;

    @Column(name = "original_file_name", nullable = false, length = 255)
    private String originalFileName;

    @Column(name = "file_content", nullable = false, columnDefinition = "TEXT")
    private String fileContent;

    @Column(name = "file_hash", length = 128)
    private String fileHash;

    @Column(name = "content_hash", length = 64)
    private String contentHash;

    @Column(name = "content_type", nullable = false, length = 32)
    private String contentType;

    @Column(name = "parse_status", nullable = false, length = 32)
    private String parseStatus;

    @Column(name = "parse_error", columnDefinition = "TEXT")
    private String parseError;

    @Column(name = "doc_type", nullable = false, length = 32)
    private String docType;

    @Column(name = "security_level", nullable = false)
    private Integer securityLevel = 1;

    @Column(name = "uploaded_by")
    private Long uploadedBy;

    @Column(name = "change_record_id")
    private Long changeRecordId;

    @Column(name = "doc_content_type", length = 32)
    private String docContentType;

    @Column(name = "markdown_content", columnDefinition = "TEXT")
    private String markdownContent;

    @Column(name = "tree_build_status", length = 32)
    private String treeBuildStatus;

    @Column(name = "merge_status", length = 32)
    private String mergeStatus;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSystemId() { return systemId; }
    public void setSystemId(Long systemId) { this.systemId = systemId; }
    public String getDocName() { return docName; }
    public void setDocName(String docName) { this.docName = docName; }
    public String getOriginalFileName() { return originalFileName; }
    public void setOriginalFileName(String originalFileName) { this.originalFileName = originalFileName; }
    public String getFileContent() { return fileContent; }
    public void setFileContent(String fileContent) { this.fileContent = fileContent; }
    public String getFileHash() { return fileHash; }
    public void setFileHash(String fileHash) { this.fileHash = fileHash; }
    public String getContentHash() { return contentHash; }
    public void setContentHash(String contentHash) { this.contentHash = contentHash; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public String getParseStatus() { return parseStatus; }
    public void setParseStatus(String parseStatus) { this.parseStatus = parseStatus; }
    public String getParseError() { return parseError; }
    public void setParseError(String parseError) { this.parseError = parseError; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public String getDocType() { return docType; }
    public void setDocType(String docType) { this.docType = docType; }
    public Integer getSecurityLevel() { return securityLevel; }
    public void setSecurityLevel(Integer securityLevel) { this.securityLevel = securityLevel; }
    public Long getUploadedBy() { return uploadedBy; }
    public void setUploadedBy(Long uploadedBy) { this.uploadedBy = uploadedBy; }
    public Long getChangeRecordId() { return changeRecordId; }
    public void setChangeRecordId(Long changeRecordId) { this.changeRecordId = changeRecordId; }
    public String getDocContentType() { return docContentType; }
    public void setDocContentType(String docContentType) { this.docContentType = docContentType; }
    public String getMarkdownContent() { return markdownContent; }
    public void setMarkdownContent(String markdownContent) { this.markdownContent = markdownContent; }
    public String getTreeBuildStatus() { return treeBuildStatus; }
    public void setTreeBuildStatus(String treeBuildStatus) { this.treeBuildStatus = treeBuildStatus; }
    public String getMergeStatus() { return mergeStatus; }
    public void setMergeStatus(String mergeStatus) { this.mergeStatus = mergeStatus; }
}

