package io.github.xiaoailazy.coexistree.auth.service;

import io.github.xiaoailazy.coexistree.audit.service.AuditLogger;
import io.github.xiaoailazy.coexistree.auth.dto.ChangePasswordRequest;
import io.github.xiaoailazy.coexistree.auth.dto.LoginRequest;
import io.github.xiaoailazy.coexistree.auth.dto.LoginResponse;
import io.github.xiaoailazy.coexistree.security.jwt.JwtUtil;
import io.github.xiaoailazy.coexistree.shared.enums.ErrorCode;
import io.github.xiaoailazy.coexistree.shared.exception.BusinessException;
import io.github.xiaoailazy.coexistree.user.entity.UserEntity;
import io.github.xiaoailazy.coexistree.user.repository.UserRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuditLogger auditLogger;

    public AuthServiceImpl(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtUtil jwtUtil, AuditLogger auditLogger) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.auditLogger = auditLogger;
    }

    @Override
    @Transactional
    public LoginResponse login(LoginRequest request) {
        UserEntity user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new BadCredentialsException("用户名或密码错误"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("用户名或密码错误");
        }

        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());

        auditLogger.logLogin(user.getId(), user.getUsername(), null, true);

        return new LoginResponse(
                token,
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getRole().name()
        );
    }

    @Override
    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "用户不存在"));

        if (!passwordEncoder.matches(request.getOldPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("旧密码错误");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    @Override
    public UserEntity getCurrentUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "用户不存在"));
    }
}
