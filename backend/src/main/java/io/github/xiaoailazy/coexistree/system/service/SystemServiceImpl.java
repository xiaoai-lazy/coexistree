package io.github.xiaoailazy.coexistree.system.service;

import io.github.xiaoailazy.coexistree.security.model.SecurityUserDetails;
import io.github.xiaoailazy.coexistree.shared.enums.ErrorCode;
import io.github.xiaoailazy.coexistree.shared.exception.BusinessException;
import io.github.xiaoailazy.coexistree.document.repository.DocumentRepository;
import io.github.xiaoailazy.coexistree.system.dto.AdminSystemResponse;
import io.github.xiaoailazy.coexistree.system.dto.CreateSystemRequest;
import io.github.xiaoailazy.coexistree.system.dto.SystemResponse;
import io.github.xiaoailazy.coexistree.system.dto.UpdateSystemRequest;
import io.github.xiaoailazy.coexistree.system.entity.RelationType;
import io.github.xiaoailazy.coexistree.system.entity.SystemEntity;
import io.github.xiaoailazy.coexistree.system.entity.SystemUserMappingEntity;
import io.github.xiaoailazy.coexistree.system.repository.SystemRepository;
import io.github.xiaoailazy.coexistree.system.repository.SystemUserMappingRepository;
import io.github.xiaoailazy.coexistree.user.entity.UserEntity;
import io.github.xiaoailazy.coexistree.user.repository.UserRepository;
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
    private final SystemUserMappingRepository systemUserMappingRepository;
    private final UserRepository userRepository;

    public SystemServiceImpl(SystemRepository systemRepository, DocumentRepository documentRepository,
                             SystemUserMappingRepository systemUserMappingRepository,
                             UserRepository userRepository) {
        this.systemRepository = systemRepository;
        this.documentRepository = documentRepository;
        this.systemUserMappingRepository = systemUserMappingRepository;
        this.userRepository = userRepository;
    }

    @Override
    public SystemEntity getEntity(Long id) {
        return systemRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.SYSTEM_NOT_FOUND, "System not found"));
    }

    @Override
    @Transactional
    public SystemResponse create(CreateSystemRequest request, SecurityUserDetails userDetails) {
        log.info("创建系统, systemCode={}, systemName={}", request.systemCode(), request.systemName());

        validateCreateRequest(request);

        SystemEntity entity = new SystemEntity();
        entity.setSystemCode(request.systemCode());
        entity.setSystemName(request.systemName());
        entity.setDescription(request.description());
        entity.setStatus("ACTIVE");
        entity.setCreatedBy(userDetails.getId());
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());

        SystemEntity saved = systemRepository.save(entity);

        // Create OWNER mapping
        SystemUserMappingEntity mapping = new SystemUserMappingEntity();
        mapping.setSystemId(saved.getId());
        mapping.setUserId(userDetails.getId());
        mapping.setRelationType(RelationType.OWNER);
        mapping.setViewLevel(5);
        mapping.setAssignedBy(userDetails.getId());
        systemUserMappingRepository.save(mapping);

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
    public List<SystemResponse> list(SecurityUserDetails userDetails) {
        if (userDetails.getRole().name().equals("SUPER_ADMIN")) {
            // Admin sees all systems
            return systemRepository.findAll().stream()
                    .map(this::toResponse)
                    .toList();
        } else {
            // Regular users see systems they're associated with
            List<SystemUserMappingEntity> mappings = systemUserMappingRepository.findByUserId(userDetails.getId());
            List<Long> systemIds = mappings.stream()
                    .map(SystemUserMappingEntity::getSystemId)
                    .toList();

            return systemRepository.findAllById(systemIds).stream()
                    .map(this::toResponse)
                    .toList();
        }
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

    @Override
    public List<AdminSystemResponse> listAllForAdmin() {
        log.info("管理员获取所有系统列表");
        return systemRepository.findAll().stream()
                .map(this::toAdminResponse)
                .toList();
    }

    @Override
    @Transactional
    public void transferOwnership(Long systemId, Long newOwnerId) {
        log.info("转移系统所有权, systemId={}, newOwnerId={}", systemId, newOwnerId);

        // Validate system exists
        SystemEntity system = getEntity(systemId);

        // Validate new owner exists
        UserEntity newOwner = userRepository.findById(newOwnerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "用户不存在"));

        // Find current owner mapping
        SystemUserMappingEntity currentOwnerMapping = systemUserMappingRepository
                .findBySystemIdAndRelationType(systemId, RelationType.OWNER)
                .stream()
                .findFirst()
                .orElse(null);

        Long oldOwnerId = currentOwnerMapping != null ? currentOwnerMapping.getUserId() : null;

        // If new owner is same as old owner, do nothing
        if (newOwnerId.equals(oldOwnerId)) {
            log.info("新所有者与当前所有者相同, 无需转移");
            return;
        }

        // Update current owner to maintainer (if exists)
        if (currentOwnerMapping != null) {
            currentOwnerMapping.setRelationType(RelationType.MAINTAINER);
            systemUserMappingRepository.save(currentOwnerMapping);
        }

        // Check if new owner already has a mapping
        SystemUserMappingEntity newOwnerMapping = systemUserMappingRepository
                .findBySystemIdAndUserId(systemId, newOwnerId)
                .orElse(null);

        if (newOwnerMapping != null) {
            // Update existing mapping to owner
            newOwnerMapping.setRelationType(RelationType.OWNER);
            newOwnerMapping.setViewLevel(5);
            systemUserMappingRepository.save(newOwnerMapping);
        } else {
            // Create new owner mapping
            newOwnerMapping = new SystemUserMappingEntity();
            newOwnerMapping.setSystemId(systemId);
            newOwnerMapping.setUserId(newOwnerId);
            newOwnerMapping.setRelationType(RelationType.OWNER);
            newOwnerMapping.setViewLevel(5);
            newOwnerMapping.setAssignedBy(oldOwnerId);
            systemUserMappingRepository.save(newOwnerMapping);
        }

        // Update system's created_by to new owner
        system.setCreatedBy(newOwnerId);
        systemRepository.save(system);

        log.info("系统所有权转移成功, systemId={}, oldOwnerId={}, newOwnerId={}", systemId, oldOwnerId, newOwnerId);
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

    private AdminSystemResponse toAdminResponse(SystemEntity entity) {
        // Find owner
        SystemUserMappingEntity ownerMapping = systemUserMappingRepository
                .findBySystemIdAndRelationType(entity.getId(), RelationType.OWNER)
                .stream()
                .findFirst()
                .orElse(null);

        Long ownerId = ownerMapping != null ? ownerMapping.getUserId() : null;
        String ownerUsername = null;

        if (ownerId != null) {
            ownerUsername = userRepository.findById(ownerId)
                    .map(UserEntity::getUsername)
                    .orElse(null);
        }

        // Count members
        int memberCount = systemUserMappingRepository.countBySystemId(entity.getId());

        return new AdminSystemResponse(
                entity.getId(),
                entity.getSystemCode(),
                entity.getSystemName(),
                entity.getDescription(),
                entity.getStatus(),
                ownerId,
                ownerUsername,
                memberCount,
                entity.getCreatedAt()
        );
    }
}
