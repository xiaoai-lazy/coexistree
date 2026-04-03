package io.github.xiaoailazy.coexistree.user.initializer;

import io.github.xiaoailazy.coexistree.user.config.AdminProperties;
import io.github.xiaoailazy.coexistree.user.entity.UserEntity;
import io.github.xiaoailazy.coexistree.user.entity.UserRole;
import io.github.xiaoailazy.coexistree.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminInitializer 测试")
class AdminInitializerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private ApplicationArguments args;

    private AdminProperties adminProperties;
    private AdminInitializer adminInitializer;

    @BeforeEach
    void setUp() {
        adminProperties = new AdminProperties();
        adminInitializer = new AdminInitializer(userRepository, passwordEncoder, adminProperties);
    }

    @Nested
    @DisplayName("初始化逻辑测试")
    class InitializationTests {

        @Test
        @DisplayName("管理员不存在时创建新管理员")
        void shouldCreateAdminWhenNotExists() {
            // Given
            adminProperties.setUsername("admin");
            adminProperties.setPassword("adminPassword123");
            adminProperties.setDisplayName("System Administrator");

            String encodedPassword = "$2a$10$encodedPassword";

            when(userRepository.existsByUsername("admin")).thenReturn(false);
            when(passwordEncoder.encode("adminPassword123")).thenReturn(encodedPassword);
            when(userRepository.save(any(UserEntity.class))).thenAnswer(inv -> {
                UserEntity saved = inv.getArgument(0);
                saved.setId(1L);
                return saved;
            });

            // When
            adminInitializer.run(args);

            // Then
            verify(userRepository).existsByUsername("admin");
            verify(passwordEncoder).encode("adminPassword123");

            ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
            verify(userRepository).save(captor.capture());

            UserEntity savedAdmin = captor.getValue();
            assertThat(savedAdmin.getUsername()).isEqualTo("admin");
            assertThat(savedAdmin.getPasswordHash()).isEqualTo(encodedPassword);
            assertThat(savedAdmin.getDisplayName()).isEqualTo("System Administrator");
            assertThat(savedAdmin.getRole()).isEqualTo(UserRole.SUPER_ADMIN);
        }

        @Test
        @DisplayName("管理员已存在时不创建")
        void shouldNotCreateAdminWhenExists() {
            // Given
            adminProperties.setUsername("admin");
            adminProperties.setPassword("adminPassword123");

            when(userRepository.existsByUsername("admin")).thenReturn(true);

            // When
            adminInitializer.run(args);

            // Then
            verify(userRepository).existsByUsername("admin");
            verify(userRepository, never()).save(any());
            verifyNoInteractions(passwordEncoder);
        }

        @Test
        @DisplayName("未配置密码时抛出异常")
        void shouldThrowExceptionWhenPasswordNotConfigured() {
            // Given - 密码默认为 null
            adminProperties.setUsername("admin");
            // adminProperties.setPassword() 未被调用，保持为 null

            // When & Then
            assertThatThrownBy(() -> adminInitializer.run(args))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Admin password must be configured");

            verifyNoInteractions(userRepository, passwordEncoder);
        }

        @Test
        @DisplayName("空密码时抛出异常")
        void shouldThrowExceptionWhenPasswordIsEmpty() {
            // Given
            adminProperties.setUsername("admin");
            adminProperties.setPassword(""); // 空字符串

            // When & Then
            assertThatThrownBy(() -> adminInitializer.run(args))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Admin password must be configured");

            verifyNoInteractions(userRepository, passwordEncoder);
        }

        @Test
        @DisplayName("空白密码时抛出异常")
        void shouldThrowExceptionWhenPasswordIsBlank() {
            // Given
            adminProperties.setUsername("admin");
            adminProperties.setPassword("   "); // 空白字符

            // When & Then
            assertThatThrownBy(() -> adminInitializer.run(args))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Admin password must be configured");

            verifyNoInteractions(userRepository, passwordEncoder);
        }
    }

    @Nested
    @DisplayName("配置属性测试")
    class ConfigurationTests {

        @Test
        @DisplayName("使用默认用户名创建管理员")
        void shouldUseDefaultUsername() {
            // Given
            // 不设置 username，使用默认值 "admin"
            adminProperties.setPassword("password123");

            when(userRepository.existsByUsername("admin")).thenReturn(false);
            when(passwordEncoder.encode(any())).thenReturn("encoded");
            when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // When
            adminInitializer.run(args);

            // Then
            verify(userRepository).existsByUsername("admin");
        }

        @Test
        @DisplayName("使用自定义用户名创建管理员")
        void shouldUseCustomUsername() {
            // Given
            adminProperties.setUsername("customadmin");
            adminProperties.setPassword("password123");

            when(userRepository.existsByUsername("customadmin")).thenReturn(false);
            when(passwordEncoder.encode(any())).thenReturn("encoded");
            when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // When
            adminInitializer.run(args);

            // Then
            verify(userRepository).existsByUsername("customadmin");
        }

        @Test
        @DisplayName("使用默认显示名称")
        void shouldUseDefaultDisplayName() {
            // Given
            adminProperties.setPassword("password123");
            // 不设置 displayName，使用默认值

            when(userRepository.existsByUsername(any())).thenReturn(false);
            when(passwordEncoder.encode(any())).thenReturn("encoded");
            when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // When
            adminInitializer.run(args);

            // Then
            ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
            verify(userRepository).save(captor.capture());
            assertThat(captor.getValue().getDisplayName()).isEqualTo("系统管理员");
        }

        @Test
        @DisplayName("使用自定义显示名称")
        void shouldUseCustomDisplayName() {
            // Given
            adminProperties.setPassword("password123");
            adminProperties.setDisplayName("Custom Admin Name");

            when(userRepository.existsByUsername(any())).thenReturn(false);
            when(passwordEncoder.encode(any())).thenReturn("encoded");
            when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // When
            adminInitializer.run(args);

            // Then
            ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
            verify(userRepository).save(captor.capture());
            assertThat(captor.getValue().getDisplayName()).isEqualTo("Custom Admin Name");
        }
    }

    @Nested
    @DisplayName("角色测试")
    class RoleTests {

        @Test
        @DisplayName("创建的管理员角色为 SUPER_ADMIN")
        void shouldCreateSuperAdminRole() {
            // Given
            adminProperties.setPassword("password123");

            when(userRepository.existsByUsername(any())).thenReturn(false);
            when(passwordEncoder.encode(any())).thenReturn("encoded");
            when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // When
            adminInitializer.run(args);

            // Then
            ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
            verify(userRepository).save(captor.capture());
            assertThat(captor.getValue().getRole()).isEqualTo(UserRole.SUPER_ADMIN);
        }
    }
}
