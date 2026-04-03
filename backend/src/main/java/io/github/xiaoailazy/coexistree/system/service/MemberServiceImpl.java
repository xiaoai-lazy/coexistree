package io.github.xiaoailazy.coexistree.system.service;

import io.github.xiaoailazy.coexistree.security.model.SecurityUserDetails;
import io.github.xiaoailazy.coexistree.system.dto.AddMemberRequest;
import io.github.xiaoailazy.coexistree.system.dto.MemberResponse;
import io.github.xiaoailazy.coexistree.system.dto.UpdateViewLevelRequest;
import io.github.xiaoailazy.coexistree.system.entity.RelationType;
import io.github.xiaoailazy.coexistree.system.entity.SystemUserMappingEntity;
import io.github.xiaoailazy.coexistree.system.repository.SystemUserMappingRepository;
import io.github.xiaoailazy.coexistree.user.entity.UserEntity;
import io.github.xiaoailazy.coexistree.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class MemberServiceImpl implements MemberService {

    private final SystemUserMappingRepository mappingRepository;
    private final UserRepository userRepository;

    public MemberServiceImpl(SystemUserMappingRepository mappingRepository, UserRepository userRepository) {
        this.mappingRepository = mappingRepository;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public MemberResponse addMember(Long systemId, AddMemberRequest request, SecurityUserDetails currentUser) {
        checkManagePermission(systemId, currentUser, request.getRelationType());

        UserEntity user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("用户不存在: " + request.getUsername()));

        if (mappingRepository.existsBySystemIdAndUserId(systemId, user.getId())) {
            throw new IllegalArgumentException("该用户已经是系统成员");
        }

        int maxViewLevel = getMaxAllowedViewLevel(systemId, currentUser);
        if (request.getViewLevel() > maxViewLevel) {
            throw new IllegalArgumentException("设置的查看等级不能超过 " + maxViewLevel);
        }

        SystemUserMappingEntity mapping = new SystemUserMappingEntity();
        mapping.setSystemId(systemId);
        mapping.setUserId(user.getId());
        mapping.setRelationType(request.getRelationType());
        mapping.setViewLevel(request.getViewLevel());
        mapping.setAssignedBy(currentUser.getId());

        SystemUserMappingEntity saved = mappingRepository.save(mapping);

        return toMemberResponse(saved, user);
    }

    @Override
    @Transactional
    public void removeMember(Long systemId, Long userId, SecurityUserDetails currentUser) {
        SystemUserMappingEntity mapping = mappingRepository.findBySystemIdAndUserId(systemId, userId)
                .orElseThrow(() -> new IllegalArgumentException("成员不存在"));

        if (mapping.getRelationType() == RelationType.OWNER) {
            throw new IllegalArgumentException("不能移除系统主人");
        }

        if (currentUser.getRole().name().equals("SUPER_ADMIN")) {
            // Admin can remove anyone
        } else {
            SystemUserMappingEntity currentMapping = mappingRepository.findBySystemIdAndUserId(systemId, currentUser.getId())
                    .orElseThrow(() -> new IllegalArgumentException("无权限"));

            if (currentMapping.getRelationType() == RelationType.MAINTAINER &&
                    mapping.getRelationType() != RelationType.SUBSCRIBER) {
                throw new IllegalArgumentException("维护人只能移除订阅者");
            }
        }

        mappingRepository.deleteBySystemIdAndUserId(systemId, userId);
    }

    @Override
    @Transactional
    public MemberResponse updateViewLevel(Long systemId, Long userId, UpdateViewLevelRequest request, SecurityUserDetails currentUser) {
        SystemUserMappingEntity mapping = mappingRepository.findBySystemIdAndUserId(systemId, userId)
                .orElseThrow(() -> new IllegalArgumentException("成员不存在"));

        if (mapping.getRelationType() == RelationType.OWNER) {
            throw new IllegalArgumentException("不能修改主人的查看等级");
        }

        int maxViewLevel = getMaxAllowedViewLevel(systemId, currentUser);
        if (request.getViewLevel() > maxViewLevel) {
            throw new IllegalArgumentException("设置的查看等级不能超过 " + maxViewLevel);
        }

        mapping.setViewLevel(request.getViewLevel());
        SystemUserMappingEntity saved = mappingRepository.save(mapping);

        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));

        return toMemberResponse(saved, user);
    }

    @Override
    public List<MemberResponse> listMembers(Long systemId, SecurityUserDetails currentUser) {
        if (!currentUser.getRole().name().equals("SUPER_ADMIN")) {
            mappingRepository.findBySystemIdAndUserId(systemId, currentUser.getId())
                    .orElseThrow(() -> new IllegalArgumentException("无权限查看此系统"));
        }

        List<SystemUserMappingEntity> mappings = mappingRepository.findBySystemId(systemId);

        return mappings.stream().map(mapping -> {
            UserEntity user = userRepository.findById(mapping.getUserId())
                    .orElseThrow(() -> new IllegalStateException("用户数据不一致"));
            return toMemberResponse(mapping, user);
        }).toList();
    }

    private void checkManagePermission(Long systemId, SecurityUserDetails currentUser, RelationType relationType) {
        if (currentUser.getRole().name().equals("SUPER_ADMIN")) {
            return;
        }

        SystemUserMappingEntity currentMapping = mappingRepository.findBySystemIdAndUserId(systemId, currentUser.getId())
                .orElseThrow(() -> new IllegalArgumentException("无权限管理此系统"));

        if (currentMapping.getRelationType() == RelationType.OWNER) {
            return;
        }

        if (currentMapping.getRelationType() == RelationType.MAINTAINER) {
            if (relationType != RelationType.SUBSCRIBER) {
                throw new IllegalArgumentException("维护人只能添加订阅者");
            }
            return;
        }

        throw new IllegalArgumentException("无权限");
    }

    private int getMaxAllowedViewLevel(Long systemId, SecurityUserDetails currentUser) {
        if (currentUser.getRole().name().equals("SUPER_ADMIN")) {
            return 5;
        }

        SystemUserMappingEntity mapping = mappingRepository.findBySystemIdAndUserId(systemId, currentUser.getId())
                .orElseThrow(() -> new IllegalArgumentException("无权限"));

        return mapping.getViewLevel();
    }

    private MemberResponse toMemberResponse(SystemUserMappingEntity mapping, UserEntity user) {
        return new MemberResponse(
                mapping.getId(),
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                mapping.getRelationType(),
                mapping.getViewLevel(),
                mapping.getAssignedAt()
        );
    }
}
