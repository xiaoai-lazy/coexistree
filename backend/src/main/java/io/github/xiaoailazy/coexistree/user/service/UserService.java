package io.github.xiaoailazy.coexistree.user.service;

import io.github.xiaoailazy.coexistree.shared.enums.ErrorCode;
import io.github.xiaoailazy.coexistree.shared.exception.BusinessException;
import io.github.xiaoailazy.coexistree.user.dto.CreateUserRequest;
import io.github.xiaoailazy.coexistree.user.dto.UpdateUserRequest;
import io.github.xiaoailazy.coexistree.user.dto.UserResponse;
import io.github.xiaoailazy.coexistree.user.entity.UserEntity;
import io.github.xiaoailazy.coexistree.user.entity.UserRole;
import io.github.xiaoailazy.coexistree.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public List<UserResponse> listAll() {
        return userRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public UserResponse createUser(CreateUserRequest request, Long createdBy) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BusinessException(ErrorCode.USERNAME_EXISTS, "用户名已存在");
        }

        UserEntity user = new UserEntity();
        user.setUsername(request.getUsername());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setDisplayName(request.getDisplayName());
        user.setRole(UserRole.USER);
        user.setCreatedBy(createdBy);

        UserEntity saved = userRepository.save(user);
        return toResponse(saved);
    }

    @Transactional
    public void deleteUser(Long userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "用户不存在"));

        if (user.getRole() == UserRole.SUPER_ADMIN) {
            throw new BusinessException(ErrorCode.CANNOT_DELETE_SUPER_ADMIN, "不能删除超级管理员");
        }

        userRepository.deleteById(userId);
    }

    @Transactional
    public void resetPassword(Long userId, String newPassword) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "用户不存在"));

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    @Transactional
    public UserResponse updateUser(Long userId, UpdateUserRequest request) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "用户不存在"));

        // 不允许修改超级管理员的角色
        if (user.getRole() == UserRole.SUPER_ADMIN && !"SUPER_ADMIN".equals(request.getRole())) {
            throw new BusinessException(ErrorCode.CANNOT_MODIFY_SUPER_ADMIN_ROLE, "不能修改超级管理员的角色");
        }

        user.setDisplayName(request.getDisplayName());
        user.setRole(UserRole.valueOf(request.getRole()));
        user.setEnabled(request.getEnabled());

        UserEntity saved = userRepository.save(user);
        return toResponse(saved);
    }

    private UserResponse toResponse(UserEntity user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getRole().name(),
                user.getEnabled(),
                user.getCreatedAt(),
                user.getLastLoginAt()
        );
    }
}
