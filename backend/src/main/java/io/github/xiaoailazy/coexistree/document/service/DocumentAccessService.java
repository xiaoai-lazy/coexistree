package io.github.xiaoailazy.coexistree.document.service;

import io.github.xiaoailazy.coexistree.document.entity.DocumentEntity;
import io.github.xiaoailazy.coexistree.security.model.SecurityUserDetails;
import io.github.xiaoailazy.coexistree.shared.enums.ErrorCode;
import io.github.xiaoailazy.coexistree.shared.exception.BusinessException;
import io.github.xiaoailazy.coexistree.system.repository.SystemUserMappingRepository;
import org.springframework.stereotype.Service;

@Service
public class DocumentAccessService {

    private final SystemUserMappingRepository systemUserMappingRepository;

    public DocumentAccessService(SystemUserMappingRepository systemUserMappingRepository) {
        this.systemUserMappingRepository = systemUserMappingRepository;
    }

    public void checkSystemAccess(Long systemId, SecurityUserDetails userDetails) {
        if (isSuperAdmin(userDetails)) {
            return;
        }

        if (systemId == null || userDetails == null || userDetails.getId() == null) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED, "无权限访问此系统");
        }

        systemUserMappingRepository.findBySystemIdAndUserId(systemId, userDetails.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PERMISSION_DENIED, "无权限访问此系统"));
    }

    public void checkDocumentAccess(DocumentEntity document, SecurityUserDetails userDetails) {
        requireCanReadDocument(document, userDetails);
    }

    public void requireCanReadDocument(DocumentEntity document, SecurityUserDetails userDetails) {
        if (!canReadDocument(document, userDetails)) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED, "无权限访问此文档");
        }
    }

    public Integer getViewLevel(Long systemId, SecurityUserDetails userDetails) {
        if (isSuperAdmin(userDetails)) {
            return 5;
        }

        if (systemId == null || userDetails == null || userDetails.getId() == null) {
            return 0;
        }

        return systemUserMappingRepository.findBySystemIdAndUserId(systemId, userDetails.getId())
                .map(mapping -> mapping.getViewLevel() != null ? mapping.getViewLevel() : 0)
                .orElse(0);
    }

    public boolean canReadDocument(DocumentEntity document, SecurityUserDetails userDetails) {
        if (document == null || document.getSystemId() == null || document.getSecurityLevel() == null || userDetails == null) {
            return false;
        }
        if (isSuperAdmin(userDetails)) {
            return true;
        }
        Integer viewLevel = getViewLevel(document.getSystemId(), userDetails);
        return document.getSecurityLevel() <= viewLevel;
    }

    private boolean isSuperAdmin(SecurityUserDetails userDetails) {
        return userDetails != null && userDetails.getRole() != null && userDetails.getRole().name().equals("SUPER_ADMIN");
    }
}
