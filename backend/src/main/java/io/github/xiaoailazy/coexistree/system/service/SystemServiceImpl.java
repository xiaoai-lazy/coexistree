package io.github.xiaoailazy.coexistree.system.service;

import io.github.xiaoailazy.coexistree.shared.enums.ErrorCode;
import io.github.xiaoailazy.coexistree.shared.exception.BusinessException;
import io.github.xiaoailazy.coexistree.document.repository.DocumentRepository;
import io.github.xiaoailazy.coexistree.system.dto.CreateSystemRequest;
import io.github.xiaoailazy.coexistree.system.dto.SystemResponse;
import io.github.xiaoailazy.coexistree.system.dto.UpdateSystemRequest;
import io.github.xiaoailazy.coexistree.system.entity.SystemEntity;
import io.github.xiaoailazy.coexistree.system.repository.SystemRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class SystemServiceImpl implements SystemService {

    private final SystemRepository systemRepository;
    private final DocumentRepository documentRepository;

    public SystemServiceImpl(SystemRepository systemRepository, DocumentRepository documentRepository) {
        this.systemRepository = systemRepository;
        this.documentRepository = documentRepository;
    }

    @Override
    public SystemEntity getEntity(Long id) {
        return systemRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.SYSTEM_NOT_FOUND, "System not found"));
    }

    @Override
    public SystemResponse create(CreateSystemRequest request) {
        log.info("创建系统, systemCode={}, systemName={}", request.systemCode(), request.systemName());

        validateCreateRequest(request);

        SystemEntity entity = new SystemEntity();
        entity.setSystemCode(request.systemCode());
        entity.setSystemName(request.systemName());
        entity.setDescription(request.description());
        entity.setStatus("ACTIVE");
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());

        SystemEntity saved = systemRepository.save(entity);
        log.info("系统创建成功, id={}", saved.getId());

        return toResponse(saved);
    }

    @Override
    public SystemResponse get(Long id) {
        return toResponse(getEntity(id));
    }

    @Override
    public SystemResponse update(Long id, UpdateSystemRequest request) {
        log.info("更新系统, id={}", id);

        SystemEntity entity = getEntity(id);

        if (StringUtils.hasText(request.systemName())) {
            entity.setSystemName(request.systemName());
        }
        if (request.description() != null) {
            entity.setDescription(request.description());
        }
        if (StringUtils.hasText(request.status())) {
            entity.setStatus(request.status());
        }
        entity.setUpdatedAt(LocalDateTime.now());

        SystemEntity saved = systemRepository.save(entity);
        log.info("系统更新成功, id={}", saved.getId());

        return toResponse(saved);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        log.info("删除系统, id={}", id);

        SystemEntity entity = getEntity(id);

        long documentCount = documentRepository.countBySystemId(id);
        if (documentCount > 0) {
            log.warn("系统下存在文档, 无法删除, id={}, documentCount={}", id, documentCount);
            throw new BusinessException(ErrorCode.SYSTEM_HAS_DOCUMENTS, 
                    "Cannot delete system with existing documents. Please delete documents first.");
        }

        systemRepository.delete(entity);
        log.info("系统删除成功, id={}", id);
    }

    @Override
    public List<SystemResponse> list() {
        return systemRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    private void validateCreateRequest(CreateSystemRequest request) {
        if (!StringUtils.hasText(request.systemCode())) {
            throw new BusinessException(ErrorCode.SYSTEM_CODE_EMPTY, "System code is required");
        }
        if (!StringUtils.hasText(request.systemName())) {
            throw new BusinessException(ErrorCode.SYSTEM_NAME_EMPTY, "System name is required");
        }
        if (systemRepository.existsBySystemCode(request.systemCode())) {
            throw new BusinessException(ErrorCode.SYSTEM_CODE_DUPLICATE, "System code already exists: " + request.systemCode());
        }
    }

    private SystemResponse toResponse(SystemEntity entity) {
        return new SystemResponse(
                entity.getId(),
                entity.getSystemCode(),
                entity.getSystemName(),
                entity.getDescription(),
                entity.getStatus()
        );
    }
}
