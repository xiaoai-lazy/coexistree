package io.github.xiaoailazy.coexistree.user.controller;

import io.github.xiaoailazy.coexistree.shared.api.ApiResponse;
import io.github.xiaoailazy.coexistree.shared.test.BaseDataIntegrationTest;
import io.github.xiaoailazy.coexistree.user.dto.CreateUserRequest;
import io.github.xiaoailazy.coexistree.user.dto.UserResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UserController 集成测试
 *
 * 继承 BaseDataIntegrationTest 自动获得预置数据：
 * - testuser (id=101): SUPER_ADMIN 角色，用于测试管理员功能
 * - otheruser (id=102): USER 角色
 * - testadmin (id=199): USER 角色
 *
 * 注意：所有接口都需要 SUPER_ADMIN 角色，因此使用 testuser 的 token 进行测试。
 */
@DisplayName("UserController 集成测试")
class UserControllerIntegrationTest extends BaseDataIntegrationTest {

    private static final String BASE_URL = "/api/v1/users";

    @Nested
    @DisplayName("查询用户列表测试")
    class ListUsersTests {

        @Test
        @DisplayName("SUPER_ADMIN 可获取用户列表")
        void shouldAllowSuperAdminToListUsers() {
            // When - testuser 是 SUPER_ADMIN
            ResponseEntity<ApiResponse<List<UserResponse>>> response = restTemplate.exchange(
                    BASE_URL,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {}
            );

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().success()).isTrue();
            assertThat(response.getBody().data()).isNotEmpty();

            // 验证预置用户存在
            List<UserResponse> users = response.getBody().data();
            assertThat(users)
                    .extracting(UserResponse::getUsername)
                    .contains("testuser", "otheruser", "testadmin");
        }
    }

    @Nested
    @DisplayName("创建用户测试")
    class CreateUserTests {

        @Test
        @DisplayName("SUPER_ADMIN 可创建新用户")
        void shouldAllowSuperAdminToCreateUser() {
            // Given
            CreateUserRequest request = new CreateUserRequest();
            request.setUsername("newusertest");
            request.setPassword("Password123");
            request.setDisplayName("New Test User");

            // When
            ResponseEntity<ApiResponse<UserResponse>> response = restTemplate.exchange(
                    BASE_URL,
                    HttpMethod.POST,
                    new HttpEntity<>(request),
                    new ParameterizedTypeReference<>() {}
            );

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().success()).isTrue();
            assertThat(response.getBody().data().getUsername()).isEqualTo("newusertest");
            assertThat(response.getBody().data().getRole()).isEqualTo("USER");
        }

        @Test
        @DisplayName("创建重复用户名应失败")
        void shouldFailWhenCreatingDuplicateUsername() {
            // Given - 使用预置的 testuser 用户名
            CreateUserRequest request = new CreateUserRequest();
            request.setUsername("testuser");
            request.setPassword("Password123");
            request.setDisplayName("Duplicate User");

            // When
            ResponseEntity<ApiResponse<UserResponse>> response = restTemplate.exchange(
                    BASE_URL,
                    HttpMethod.POST,
                    new HttpEntity<>(request),
                    new ParameterizedTypeReference<>() {}
            );

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().success()).isFalse();
        }

        @Test
        @DisplayName("创建用户时密码不符合规则应失败")
        void shouldFailWhenPasswordInvalid() {
            // Given - 密码太短
            CreateUserRequest request = new CreateUserRequest();
            request.setUsername("validusername");
            request.setPassword("short");
            request.setDisplayName("Valid User");

            // When
            ResponseEntity<ApiResponse<UserResponse>> response = restTemplate.exchange(
                    BASE_URL,
                    HttpMethod.POST,
                    new HttpEntity<>(request),
                    new ParameterizedTypeReference<>() {}
            );

            // Then
            assertThat(response.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.BAD_REQUEST);
            if (response.getBody() != null) {
                assertThat(response.getBody().success()).isFalse();
            }
        }
    }

    @Nested
    @DisplayName("删除用户测试")
    class DeleteUserTests {

        @Test
        @DisplayName("SUPER_ADMIN 可删除普通用户")
        void shouldAllowSuperAdminToDeleteNormalUser() {
            // Given - 先创建一个用户，然后删除它
            CreateUserRequest createRequest = new CreateUserRequest();
            createRequest.setUsername("usertodelete");
            createRequest.setPassword("Password123");
            createRequest.setDisplayName("User To Delete");

            ResponseEntity<ApiResponse<UserResponse>> createResponse = restTemplate.exchange(
                    BASE_URL,
                    HttpMethod.POST,
                    new HttpEntity<>(createRequest),
                    new ParameterizedTypeReference<ApiResponse<UserResponse>>() {}
            );

            assertThat(createResponse.getBody()).isNotNull();
            assertThat(createResponse.getBody().success()).isTrue();
            Long userId = createResponse.getBody().data().getId();

            // When
            ResponseEntity<ApiResponse<Void>> deleteResponse = restTemplate.exchange(
                    BASE_URL + "/" + userId,
                    HttpMethod.DELETE,
                    null,
                    new ParameterizedTypeReference<>() {}
            );

            // Then
            assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(deleteResponse.getBody()).isNotNull();
            assertThat(deleteResponse.getBody().success()).isTrue();
        }

        @Test
        @DisplayName("删除不存在用户应返回错误")
        void shouldFailWhenDeletingNonExistentUser() {
            // Given
            Long nonExistentId = 99999L;

            // When
            ResponseEntity<ApiResponse<Void>> response = restTemplate.exchange(
                    BASE_URL + "/" + nonExistentId,
                    HttpMethod.DELETE,
                    null,
                    new ParameterizedTypeReference<>() {}
            );

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().success()).isFalse();
        }
    }

    @Nested
    @DisplayName("重置密码测试")
    class ResetPasswordTests {

        @Test
        @DisplayName("SUPER_ADMIN 可重置用户密码")
        void shouldAllowSuperAdminToResetPassword() {
            // Given - 使用预置的 otheruser (id=102)
            Long userId = 102L;
            String newPassword = "NewPassword123";

            // When
            ResponseEntity<ApiResponse<Void>> response = restTemplate.exchange(
                    BASE_URL + "/" + userId + "/password",
                    HttpMethod.PUT,
                    new HttpEntity<>(newPassword),
                    new ParameterizedTypeReference<>() {}
            );

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().success()).isTrue();
        }

        @Test
        @DisplayName("重置不存在用户的密码应失败")
        void shouldFailWhenResettingPasswordForNonExistentUser() {
            // Given
            Long nonExistentId = 99999L;
            String newPassword = "NewPassword123";

            // When
            ResponseEntity<ApiResponse<Void>> response = restTemplate.exchange(
                    BASE_URL + "/" + nonExistentId + "/password",
                    HttpMethod.PUT,
                    new HttpEntity<>(newPassword),
                    new ParameterizedTypeReference<>() {}
            );

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().success()).isFalse();
        }
    }
}
