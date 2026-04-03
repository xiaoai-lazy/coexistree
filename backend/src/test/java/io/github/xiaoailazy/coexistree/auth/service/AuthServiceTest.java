package io.github.xiaoailazy.coexistree.auth.service;

import io.github.xiaoailazy.coexistree.auth.dto.ChangePasswordRequest;
import io.github.xiaoailazy.coexistree.auth.dto.LoginRequest;
import io.github.xiaoailazy.coexistree.auth.dto.LoginResponse;
import io.github.xiaoailazy.coexistree.security.jwt.JwtUtil;
import io.github.xiaoailazy.coexistree.shared.exception.BusinessException;
import io.github.xiaoailazy.coexistree.user.entity.UserEntity;
import io.github.xiaoailazy.coexistree.user.entity.UserRole;
import io.github.xiaoailazy.coexistree.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService 测试")
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(userRepository, passwordEncoder, jwtUtil);
    }

    private UserEntity createTestUser(Long id, String username, UserRole role) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setUsername(username);
        user.setPasswordHash("$2a$10$encodedPassword");
        user.setDisplayName("Test User");
        user.setRole(role);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        return user;
    }

    @Nested
    @DisplayName("登录测试")
    class LoginTests {

        @Test
        @DisplayName("使用正确凭据登录成功")
        void shouldLoginSuccessfullyWithValidCredentials() {
            // Given
            String username = "testuser";
            String password = "password123";
            String token = "mock.jwt.token";
            UserEntity user = createTestUser(1L, username, UserRole.USER);

            when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(password, user.getPasswordHash())).thenReturn(true);
            when(jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole())).thenReturn(token);
            when(userRepository.save(any(UserEntity.class))).thenReturn(user);

            LoginRequest request = new LoginRequest();
            request.setUsername(username);
            request.setPassword(password);

            // When
            LoginResponse response = authService.login(request);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getToken()).isEqualTo(token);
            assertThat(response.getUserId()).isEqualTo(user.getId());
            assertThat(response.getUsername()).isEqualTo(username);
            assertThat(response.getDisplayName()).isEqualTo(user.getDisplayName());
            assertThat(response.getRole()).isEqualTo(UserRole.USER.name());

            verify(userRepository).findByUsername(username);
            verify(passwordEncoder).matches(password, user.getPasswordHash());
            verify(jwtUtil).generateToken(user.getId(), user.getUsername(), user.getRole());
            verify(userRepository).save(any(UserEntity.class));
        }

        @Test
        @DisplayName("使用不存在用户名登录失败")
        void shouldThrowExceptionWhenUserNotFound() {
            // Given
            String username = "nonexistent";
            when(userRepository.findByUsername(username)).thenReturn(Optional.empty());

            LoginRequest request = new LoginRequest();
            request.setUsername(username);
            request.setPassword("password");

            // When & Then
            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(BadCredentialsException.class)
                    .hasMessage("用户名或密码错误");

            verify(userRepository).findByUsername(username);
            verifyNoInteractions(passwordEncoder, jwtUtil);
        }

        @Test
        @DisplayName("使用错误密码登录失败")
        void shouldThrowExceptionWhenPasswordIncorrect() {
            // Given
            String username = "testuser";
            String password = "wrongPassword";
            UserEntity user = createTestUser(1L, username, UserRole.USER);

            when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(password, user.getPasswordHash())).thenReturn(false);

            LoginRequest request = new LoginRequest();
            request.setUsername(username);
            request.setPassword(password);

            // When & Then
            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(BadCredentialsException.class)
                    .hasMessage("用户名或密码错误");

            verify(userRepository).findByUsername(username);
            verify(passwordEncoder).matches(password, user.getPasswordHash());
            verifyNoInteractions(jwtUtil);
        }

        @Test
        @DisplayName("登录时更新最后登录时间")
        void shouldUpdateLastLoginTimeOnSuccessfulLogin() {
            // Given
            String username = "testuser";
            String password = "password123";
            UserEntity user = createTestUser(1L, username, UserRole.USER);
            user.setLastLoginAt(null);

            when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(password, user.getPasswordHash())).thenReturn(true);
            when(jwtUtil.generateToken(any(), any(), any())).thenReturn("token");
            when(userRepository.save(any(UserEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            LoginRequest request = new LoginRequest();
            request.setUsername(username);
            request.setPassword(password);

            // When
            authService.login(request);

            // Then
            verify(userRepository).save(argThat(savedUser ->
                    savedUser.getLastLoginAt() != null
            ));
        }
    }

    @Nested
    @DisplayName("修改密码测试")
    class ChangePasswordTests {

        @Test
        @DisplayName("使用正确旧密码修改成功")
        void shouldChangePasswordWithCorrectOldPassword() {
            // Given
            Long userId = 1L;
            String oldPassword = "oldPassword123";
            String newPassword = "NewPassword123";
            String encodedNewPassword = "$2a$10$newEncodedPassword";
            String originalPasswordHash = "$2a$10$encodedPassword";
            UserEntity user = createTestUser(userId, "testuser", UserRole.USER);

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(oldPassword, originalPasswordHash)).thenReturn(true);
            when(passwordEncoder.encode(newPassword)).thenReturn(encodedNewPassword);
            when(userRepository.save(any(UserEntity.class))).thenReturn(user);

            ChangePasswordRequest request = new ChangePasswordRequest();
            request.setOldPassword(oldPassword);
            request.setNewPassword(newPassword);

            // When
            authService.changePassword(userId, request);

            // Then
            verify(userRepository).findById(userId);
            verify(passwordEncoder).matches(oldPassword, originalPasswordHash);
            verify(passwordEncoder).encode(newPassword);
            verify(userRepository).save(argThat(savedUser ->
                    savedUser.getPasswordHash().equals(encodedNewPassword)
            ));
        }

        @Test
        @DisplayName("用户不存在时抛出异常")
        void shouldThrowExceptionWhenUserNotFound() {
            // Given
            Long userId = 999L;
            when(userRepository.findById(userId)).thenReturn(Optional.empty());

            ChangePasswordRequest request = new ChangePasswordRequest();
            request.setOldPassword("oldPassword");
            request.setNewPassword("newPassword");

            // When & Then
            assertThatThrownBy(() -> authService.changePassword(userId, request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("用户不存在");

            verify(userRepository).findById(userId);
            verifyNoInteractions(passwordEncoder);
        }

        @Test
        @DisplayName("使用错误旧密码修改失败")
        void shouldThrowExceptionWhenOldPasswordIncorrect() {
            // Given
            Long userId = 1L;
            String wrongOldPassword = "wrongPassword";
            UserEntity user = createTestUser(userId, "testuser", UserRole.USER);

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(wrongOldPassword, user.getPasswordHash())).thenReturn(false);

            ChangePasswordRequest request = new ChangePasswordRequest();
            request.setOldPassword(wrongOldPassword);
            request.setNewPassword("newPassword");

            // When & Then
            assertThatThrownBy(() -> authService.changePassword(userId, request))
                    .isInstanceOf(BadCredentialsException.class)
                    .hasMessage("旧密码错误");

            verify(userRepository).findById(userId);
            verify(passwordEncoder).matches(wrongOldPassword, user.getPasswordHash());
            verify(passwordEncoder, never()).encode(any());
        }
    }

    @Nested
    @DisplayName("获取当前用户测试")
    class GetCurrentUserTests {

        @Test
        @DisplayName("获取存在的用户")
        void shouldReturnUserWhenExists() {
            // Given
            Long userId = 1L;
            UserEntity user = createTestUser(userId, "testuser", UserRole.USER);
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));

            // When
            UserEntity result = authService.getCurrentUser(userId);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(userId);
            assertThat(result.getUsername()).isEqualTo("testuser");
        }

        @Test
        @DisplayName("用户不存在时抛出异常")
        void shouldThrowExceptionWhenUserNotFound() {
            // Given
            Long userId = 999L;
            when(userRepository.findById(userId)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> authService.getCurrentUser(userId))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("用户不存在");
        }
    }
}
