package io.github.xiaoailazy.coexistree.document.service;

import io.github.xiaoailazy.coexistree.document.dto.ChangeDocumentUploadCommand;
import io.github.xiaoailazy.coexistree.document.dto.DocumentContentResponse;
import io.github.xiaoailazy.coexistree.document.dto.DocumentResponse;
import io.github.xiaoailazy.coexistree.security.model.SecurityUserDetails;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface DocumentService {

    DocumentResponse upload(MultipartFile file, Long systemId, Integer securityLevel, SecurityUserDetails userDetails);

    /**
     * 上传文档到未应用的变更批次；事务内对 {@code system_change_records} 行加写锁并校验
     * {@code tree_version_after == null}（设计 §3.4）。
     */
    DocumentResponse uploadForChange(
            Long systemId,
            Long changeRecordId,
            ChangeDocumentUploadCommand command,
            SecurityUserDetails userDetails);

    DocumentResponse getById(Long documentId);

    List<DocumentResponse> listBySystem(Long systemId, SecurityUserDetails userDetails);

    void delete(Long documentId);

    void updateSecurityLevel(Long documentId, Integer securityLevel, SecurityUserDetails userDetails);

    // Get document content for preview
    DocumentContentResponse getContent(Long documentId, SecurityUserDetails userDetails);
}
