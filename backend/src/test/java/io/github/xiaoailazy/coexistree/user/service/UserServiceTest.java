package io.github.xiaoailazy.coexistree.user.service;

import io.github.xiaoailazy.coexistree.shared.exception.BusinessException;
import io.github.xiaoailazy.coexistree.user.dto.CreateUserRequest;
import io.github.xiaoailazy.coexistree.user.dto.UpdateUserRequest;
import io.github.xiaoailazy.coexistree.user.dto.UserResponse;
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
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService 测试")
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, passwordEncoder);
    }

    private UserEntity createTestUser(Long id, String username, UserRole role) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setUsername(username);
        user.setPasswordHash("$2a$10$encoded");
        user.setDisplayName("Test User");
        user.setRole(role);
        user.setEnabled(true);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        return user;
    }

    @Nested
    @DisplayName("查询用户测试")
    class ListAllTests {

        @Test
        @DisplayName("返回所有用户列表")
        void shouldReturnAllUsers() {
            // Given
            UserEntity user1 = createTestUser(1L, "user1", UserRole.USER);
            UserEntity user2 = createTestUser(2L, "user2", UserRole.USER);
            when(userRepository.findAll()).thenReturn(List.of(user1, user2));

            // When
            List<UserResponse> result = userService.listAll();

            // Then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getUsername()).isEqualTo("user1");
            assertThat(result.get(1).getUsername()).isEqualTo("user2");
        }

        @Test
        @DisplayName("用户列表为空时返回空列表")
        void shouldReturnEmptyListWhenNoUsers() {
            // Given
            when(userRepository.findAll()).thenReturn(List.of());

            // When
            List<UserResponse> result = userService.listAll();

            // Then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("创建用户测试")
    class CreateUserTests {

        @Test
        @DisplayName("成功创建新用户")
        void shouldCreateNewUserSuccessfully() {
            // Given
            Long createdBy = 1L;
            String rawPassword = "Password123";
            String encodedPassword = "$2a$10$encoded";

            CreateUserRequest request = new CreateUserRequest();
            request.setUsername("newuser");
            request.setPassword(rawPassword);
            request.setDisplayName("New User");

            when(userRepository.existsByUsername("newuser")).thenReturn(false);
            when(passwordEncoder.encode(rawPassword)).thenReturn(encodedPassword);
            when(userRepository.save(any(UserEntity.class))).thenAnswer(inv -> {
                UserEntity saved = inv.getArgument(0);
                saved.setId(100L);
                return saved;
            });

            // When
            UserResponse result = userService.createUser(request, createdBy);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(100L);
            assertThat(result.getUsername()).isEqualTo("newuser");
            assertThat(result.getDisplayName()).isEqualTo("New User");
            assertThat(result.getRole()).isEqualTo(UserRole.USER.name());

            verify(userRepository).existsByUsername("newuser");
            verify(passwordEncoder).encode(rawPassword);
            verify(userRepository).save(any(UserEntity.class));
        }

        @Test
        @DisplayName("用户名已存在时抛出异常")
        void shouldThrowExceptionWhenUsernameExists() {
            // Given
            CreateUserRequest request = new CreateUserRequest();
            request.setUsername("existinguser");
            request.setPassword("Password123");
            request.setDisplayName("Existing User");

            when(userRepository.existsByUsername("existinguser")).thenReturn(true);

            // When & Then
            assertThatThrownBy(() -> userService.createUser(request, 1L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("用户名已存在");

            verify(userRepository).existsByUsername("existinguser");
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("新用户角色默认为 USER")
        void shouldSetDefaultRoleToUser() {
            // Given
            CreateUserRequest request = new CreateUserRequest();
            request.setUsername("newuser");
            request.setPassword("Password123");
            request.setDisplayName("New User");

            when(userRepository.existsByUsername(any())).thenReturn(false);
            when(passwordEncoder.encode(any())).thenReturn("encoded");
            when(userRepository.save(any(UserEntity.class))).thenAnswer(inv -> {
                UserEntity saved = inv.getArgument(0);
                saved.setId(1L);
                return saved;
            });

            // When
            UserResponse result = userService.createUser(request, 1L);

            // Then
            assertThat(result.getRole()).isEqualTo(UserRole.USER.name());
        }
    }

    @Nested
    @DisplayName("删除用户测试")
    class DeleteUserTests {

        @Test
        @DisplayName("成功删除普通用户")
        void shouldDeleteNormalUser() {
            // Given
            Long userId = 2L;
            UserEntity user = createTestUser(userId, "normaluser", UserRole.USER);

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));

            // When
            userService.deleteUser(userId);

            // Then
            verify(userRepository).findById(userId);
            verify(userRepository).deleteById(userId);
        }

        @Test
        @DisplayName("用户不存在时抛出异常")
        void shouldThrowExceptionWhenUserNotFound() {
            // Given
            Long userId = 999L;
            when(userRepository.findById(userId)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> userService.deleteUser(userId))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("用户不存在");

            verify(userRepository).findById(userId);
            verify(userRepository, never()).deleteById(any());
        }

        @Test
        @DisplayName("不能删除超级管理员")
        void shouldNotDeleteSuperAdmin() {
            // Given
            Long userId = 1L;
            UserEntity admin = createTestUser(userId, "admin", UserRole.SUPER_ADMIN);

            when(userRepository.findById(userId)).thenReturn(Optional.of(admin));

            // When & Then
            assertThatThrownBy(() -> userService.deleteUser(userId))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("不能删除超级管理员");

            verify(userRepository).findById(userId);
            verify(userRepository, never()).deleteById(any());
        }
    }

    @Nested
    @DisplayName("重置密码测试")
    class ResetPasswordTests {

        @Test
        @DisplayName("成功重置用户密码")
        void shouldResetPasswordSuccessfully() {
            // Given
            Long userId = 1L;
            String newPassword = "NewPassword123";
            String encodedPassword = "$2a$10$newEncoded";
            UserEntity user = createTestUser(userId, "testuser", UserRole.USER);

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(passwordEncoder.encode(newPassword)).thenReturn(encodedPassword);
            when(userRepository.save(any(UserEntity.class))).thenReturn(user);

            // When
            userService.resetPassword(userId, newPassword);

            // Then
            verify(userRepository).findById(userId);
            verify(passwordEncoder).encode(newPassword);
            verify(userRepository).save(argThat(saved ->
                    saved.getPasswordHash().equals(encodedPassword)
            ));
        }

        @Test
        @DisplayName("用户不存在时抛出异常")
        void shouldThrowExceptionWhenUserNotFound() {
            // Given
            Long userId = 999L;
            when(userRepository.findById(userId)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> userService.resetPassword(userId, "newPassword"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("用户不存在");

            verify(userRepository).findById(userId);
            verify(passwordEncoder, never()).encode(any());
        }
    }

    @Nested
    @DisplayName("更新用户测试")
    class UpdateUserTests {

        @Test
        @DisplayName("成功更新用户信息")
        void shouldUpdateUserSuccessfully() {
            // Given
            Long userId = 1L;
            UserEntity user = createTestUser(userId, "testuser", UserRole.USER);

            UpdateUserRequest request = new UpdateUserRequest();
            request.setDisplayName("Updated Name");
            request.setRole("SUPER_ADMIN");
            request.setEnabled(false);

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(userRepository.save(any(UserEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            UserResponse result = userService.updateUser(userId, request);

            // Then
            assertThat(result.getDisplayName()).isEqualTo("Updated Name");
            assertThat(result.getRole()).isEqualTo("SUPER_ADMIN");
            assertThat(result.getEnabled()).isFalse();
        }

        @Test
        @DisplayName("用户不存在时抛出异常")
        void shouldThrowExceptionWhenUserNotFound() {
            // Given
            Long userId = 999L;
            UpdateUserRequest request = new UpdateUserRequest();
            request.setDisplayName("Name");
            request.setRole("USER");
            request.setEnabled(true);

            when(userRepository.findById(userId)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> userService.updateUser(userId, request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("用户不存在");
        }

        @Test
        @DisplayName("不能修改超级管理员的角色")
        void shouldNotChangeSuperAdminRole() {
            // Given
            Long userId = 1L;
            UserEntity admin = createTestUser(userId, "admin", UserRole.SUPER_ADMIN);

            UpdateUserRequest request = new UpdateUserRequest();
            request.setDisplayName("Admin");
            request.setRole("USER"); // 尝试改为普通用户
            request.setEnabled(true);

            when(userRepository.findById(userId)).thenReturn(Optional.of(admin));

            // When & Then
            assertThatThrownBy(() -> userService.updateUser(userId, request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("不能修改超级管理员的角色");
        }
    }
}
