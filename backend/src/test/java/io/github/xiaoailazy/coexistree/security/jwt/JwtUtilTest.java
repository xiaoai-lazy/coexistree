package io.github.xiaoailazy.coexistree.security.jwt;

import io.github.xiaoailazy.coexistree.user.entity.UserRole;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JwtUtil 测试")
class JwtUtilTest {

    private static final String SECRET = "this-is-a-test-secret-key-that-must-be-at-least-32-characters-long";
    private static final long EXPIRATION = 86400000; // 24 hours
    private static final String ISSUER = "CoExistree";

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret(SECRET);
        properties.setExpiration(EXPIRATION);
        properties.setIssuer(ISSUER);
        jwtUtil = new JwtUtil(properties);
    }

    @Nested
    @DisplayName("Token 生成测试")
    class GenerateTokenTests {

        @Test
        @DisplayName("成功生成 Token")
        void shouldGenerateValidToken() {
            // Given
            Long userId = 1L;
            String username = "testuser";
            UserRole role = UserRole.USER;

            // When
            String token = jwtUtil.generateToken(userId, username, role);

            // Then
            assertThat(token).isNotNull();
            assertThat(token).isNotBlank();
            assertThat(token.split("\\.")).hasSize(3); // JWT has 3 parts
        }

        @Test
        @DisplayName("生成的 Token 包含正确的用户信息")
        void shouldGenerateTokenWithCorrectClaims() {
            // Given
            Long userId = 1L;
            String username = "testuser";
            UserRole role = UserRole.SUPER_ADMIN;

            // When
            String token = jwtUtil.generateToken(userId, username, role);
            Claims claims = jwtUtil.parseToken(token);

            // Then
            assertThat(claims.getSubject()).isEqualTo(String.valueOf(userId));
            assertThat(claims.get("username", String.class)).isEqualTo(username);
            assertThat(claims.get("role", String.class)).isEqualTo(role.name());
            assertThat(claims.getIssuer()).isEqualTo(ISSUER);
        }
    }

    @Nested
    @DisplayName("Token 解析测试")
    class ParseTokenTests {

        @Test
        @DisplayName("成功解析 Token")
        void shouldParseValidToken() {
            // Given
            Long userId = 1L;
            String username = "testuser";
            UserRole role = UserRole.USER;
            String token = jwtUtil.generateToken(userId, username, role);

            // When
            Claims claims = jwtUtil.parseToken(token);

            // Then
            assertThat(claims).isNotNull();
            assertThat(claims.getSubject()).isEqualTo(String.valueOf(userId));
            assertThat(claims.get("username", String.class)).isEqualTo(username);
            assertThat(claims.get("role", String.class)).isEqualTo(role.name());
        }
    }

    @Nested
    @DisplayName("Token 验证测试")
    class ValidateTokenTests {

        @Test
        @DisplayName("验证有效的 Token")
        void shouldValidateValidToken() {
            // Given
            String token = jwtUtil.generateToken(1L, "testuser", UserRole.USER);

            // When
            boolean isValid = jwtUtil.validateToken(token);

            // Then
            assertThat(isValid).isTrue();
        }

        @Test
        @DisplayName("验证无效的 Token 格式")
        void shouldInvalidateMalformedToken() {
            // Given
            String invalidToken = "invalid.token.format";

            // When
            boolean isValid = jwtUtil.validateToken(invalidToken);

            // Then
            assertThat(isValid).isFalse();
        }

        @Test
        @DisplayName("验证随机字符串")
        void shouldInvalidateRandomString() {
            // Given
            String randomToken = "thisIsNotAValidToken";

            // When
            boolean isValid = jwtUtil.validateToken(randomToken);

            // Then
            assertThat(isValid).isFalse();
        }
    }

    @Nested
    @DisplayName("Token 字段提取测试")
    class ExtractClaimsTests {

        @Test
        @DisplayName("提取用户 ID")
        void shouldExtractUserId() {
            // Given
            Long userId = 42L;
            String token = jwtUtil.generateToken(userId, "testuser", UserRole.USER);

            // When
            Long extractedUserId = jwtUtil.getUserIdFromToken(token);

            // Then
            assertThat(extractedUserId).isEqualTo(userId);
        }

        @Test
        @DisplayName("提取用户名")
        void shouldExtractUsername() {
            // Given
            String username = "john_doe";
            String token = jwtUtil.generateToken(1L, username, UserRole.USER);

            // When
            String extractedUsername = jwtUtil.getUsernameFromToken(token);

            // Then
            assertThat(extractedUsername).isEqualTo(username);
        }

        @Test
        @DisplayName("提取用户角色")
        void shouldExtractUserRole() {
            // Given
            UserRole role = UserRole.SUPER_ADMIN;
            String token = jwtUtil.generateToken(1L, "admin", role);

            // When
            UserRole extractedRole = jwtUtil.getRoleFromToken(token);

            // Then
            assertThat(extractedRole).isEqualTo(role);
        }
    }

    @Nested
    @DisplayName("不同角色 Token 测试")
    class DifferentRoleTests {

        @Test
        @DisplayName("生成 USER 角色的 Token")
        void shouldGenerateTokenForUserRole() {
            // Given
            Long userId = 1L;
            String username = "regularuser";
            UserRole role = UserRole.USER;

            // When
            String token = jwtUtil.generateToken(userId, username, role);

            // Then
            assertThat(jwtUtil.validateToken(token)).isTrue();
            assertThat(jwtUtil.getRoleFromToken(token)).isEqualTo(UserRole.USER);
        }

        @Test
        @DisplayName("生成 SUPER_ADMIN 角色的 Token")
        void shouldGenerateTokenForSuperAdminRole() {
            // Given
            Long userId = 99L;
            String username = "admin";
            UserRole role = UserRole.SUPER_ADMIN;

            // When
            String token = jwtUtil.generateToken(userId, username, role);

            // Then
            assertThat(jwtUtil.validateToken(token)).isTrue();
            assertThat(jwtUtil.getRoleFromToken(token)).isEqualTo(UserRole.SUPER_ADMIN);
        }
    }

    @Nested
    @DisplayName("不同用户 Token 测试")
    class DifferentUserTests {

        @Test
        @DisplayName("为不同用户生成不同 Token")
        void shouldGenerateDifferentTokensForDifferentUsers() {
            // Given
            String token1 = jwtUtil.generateToken(1L, "user1", UserRole.USER);
            String token2 = jwtUtil.generateToken(2L, "user2", UserRole.USER);

            // Then
            assertThat(token1).isNotEqualTo(token2);
            assertThat(jwtUtil.getUserIdFromToken(token1)).isEqualTo(1L);
            assertThat(jwtUtil.getUserIdFromToken(token2)).isEqualTo(2L);
        }
    }
}
