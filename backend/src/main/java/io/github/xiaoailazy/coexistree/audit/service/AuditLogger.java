package io.github.xiaoailazy.coexistree.audit.service;

import io.github.xiaoailazy.coexistree.audit.entity.AuditLogEntity;
import io.github.xiaoailazy.coexistree.audit.repository.AuditLogRepository;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class AuditLogger {

    private final AuditLogRepository auditLogRepository;

    public AuditLogger(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logLogin(Long userId, String username, String ipAddress, boolean success) {
        AuditLogEntity entity = new AuditLogEntity();
        entity.setCorrelationId(MDC.get("correlationId"));
        entity.setUserId(userId);
        entity.setUsername(username);
        entity.setActionType("LOGIN");
        entity.setEntityType("USER");
        entity.setEntityId(userId);
        entity.setIpAddress(ipAddress);
        entity.setSuccess(success);
        entity.setCreatedAt(LocalDateTime.now());
        auditLogRepository.save(entity);
    }
}
