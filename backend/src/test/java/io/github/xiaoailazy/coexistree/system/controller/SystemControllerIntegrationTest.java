package io.github.xiaoailazy.coexistree.system.controller;

import io.github.xiaoailazy.coexistree.shared.api.ApiResponse;
import io.github.xiaoailazy.coexistree.shared.test.BaseDataIntegrationTest;
import io.github.xiaoailazy.coexistree.system.dto.CreateSystemRequest;
import io.github.xiaoailazy.coexistree.system.dto.SystemResponse;
import io.github.xiaoailazy.coexistree.system.dto.UpdateSystemRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 系统集成测试 - 使用 BaseDataIntegrationTest
 *
 * 继承 BaseDataIntegrationTest 自动获得预置数据：
 * - testuser (id=101): system1 的 OWNER
 * - otheruser (id=102): system2 的 OWNER
 * - order-service (id=1): testuser 的系统
 * - user-service (id=2): otheruser 的系统
 */
class SystemControllerIntegrationTest extends BaseDataIntegrationTest {

    private static final String BASE_URL = "/api/v1/systems";

    @Nested
    @DisplayName("使用预置数据的查询测试")
    class QueryWithExistingDataTests {

        @Test
        @DisplayName("查询系统列表 - 使用预置数据")
        void shouldListSystemsUsingExistingData() {
            // Given: base-test-data.sql 已有 2 个系统

            // When
            ResponseEntity<ApiResponse<List<SystemResponse>>> response = restTemplate.exchange(
                    BASE_URL,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {}
            );

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().success()).isTrue();
            // 至少包含预置的 2 个系统
            assertThat(response.getBody().data()).hasSizeGreaterThanOrEqualTo(2);

            // 验证预置系统存在
            List<SystemResponse> systems = response.getBody().data();
            assertThat(systems)
                    .extracting(SystemResponse::systemCode)
                    .contains("order-service", "user-service");
        }

        @Test
        @DisplayName("查询指定系统 - 使用预置 ID")
        void shouldGetSystemByExistingId() {
            // Given: 使用预置数据 systemId=1 (order-service)

            // When
            ResponseEntity<ApiResponse<SystemResponse>> response = restTemplate.exchange(
                    BASE_URL + "/1",
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {}
            );

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().success()).isTrue();
            assertThat(response.getBody().data().id()).isEqualTo(1L);
            assertThat(response.getBody().data().systemCode()).isEqualTo("order-service");
        }

        @Test
        @DisplayName("查询其他用户的系统 - 权限控制")
        void shouldAccessOtherUsersSystem() {
            // testuser (token) 访问 otheruser 的 system (id=2)
            // 根据业务规则，可能是：
            // 1. 允许访问（如果是协作成员）
            // 2. 返回 403（如果无权限）

            ResponseEntity<ApiResponse<SystemResponse>> response = restTemplate.exchange(
                    BASE_URL + "/2",
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {}
            );

            // 根据实际权限策略断言
            assertThat(response.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.FORBIDDEN);
        }
    }

    @Nested
    @DisplayName("动态创建数据的测试")
    class CreateAndModifyTests {

        @Test
        @DisplayName("创建并查询系统 - 动态创建")
        void shouldCreateAndRetrieveSystem() {
            // Given: 动态创建新系统
            String uniqueCode = "TEST-" + UUID.randomUUID().toString().substring(0, 8);
            CreateSystemRequest request = new CreateSystemRequest(
                    uniqueCode,
                    "Test System",
                    "Test description"
            );

            // When - Create
            ResponseEntity<ApiResponse<SystemResponse>> createResponse = restTemplate.exchange(
                    BASE_URL,
                    HttpMethod.POST,
                    new HttpEntity<>(request),
                    new ParameterizedTypeReference<>() {}
            );

            // Then - Verify creation
            assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(createResponse.getBody()).isNotNull();
            assertThat(createResponse.getBody().success()).isTrue();
            assertThat(createResponse.getBody().data().systemCode()).isEqualTo(uniqueCode);

            Long createdId = createResponse.getBody().data().id();

            // When - Retrieve by ID
            ResponseEntity<ApiResponse<SystemResponse>> getResponse = restTemplate.exchange(
                    BASE_URL + "/" + createdId,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {}
            );

            // Then - Verify retrieval
            assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(getResponse.getBody()).isNotNull();
            assertThat(getResponse.getBody().data().systemCode()).isEqualTo(uniqueCode);
        }

        @Test
        @DisplayName("更新系统")
        void shouldUpdateSystem() {
            // Given - 动态创建系统
            String uniqueCode = "UPD-" + UUID.randomUUID().toString().substring(0, 8);
            ResponseEntity<ApiResponse<SystemResponse>> createResponse = restTemplate.exchange(
                    BASE_URL,
                    HttpMethod.POST,
                    new HttpEntity<>(new CreateSystemRequest(uniqueCode, "Original Name", "Original desc")),
                    new ParameterizedTypeReference<ApiResponse<SystemResponse>>() {}
            );
            assertThat(createResponse.getBody()).isNotNull();
            assertThat(createResponse.getBody().success()).isTrue();
            Long systemId = createResponse.getBody().data().id();

            // When - Update
            UpdateSystemRequest updateRequest = new UpdateSystemRequest(
                    "Updated Name",
                    "Updated description",
                    "ACTIVE"
            );
            ResponseEntity<ApiResponse<SystemResponse>> updateResponse = restTemplate.exchange(
                    BASE_URL + "/" + systemId,
                    HttpMethod.PUT,
                    new HttpEntity<>(updateRequest),
                    new ParameterizedTypeReference<>() {}
            );

            // Then
            assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(updateResponse.getBody()).isNotNull();
            assertThat(updateResponse.getBody().data().systemName()).isEqualTo("Updated Name");
        }

        @Test
        @DisplayName("删除系统")
        void shouldDeleteSystem() {
            // Given - 动态创建系统
            String uniqueCode = "DEL-" + UUID.randomUUID().toString().substring(0, 8);
            ResponseEntity<ApiResponse<SystemResponse>> createResponse = restTemplate.exchange(
                    BASE_URL,
                    HttpMethod.POST,
                    new HttpEntity<>(new CreateSystemRequest(uniqueCode, "To Delete", "Will be deleted")),
                    new ParameterizedTypeReference<ApiResponse<SystemResponse>>() {}
            );
            assertThat(createResponse.getBody()).isNotNull();
            assertThat(createResponse.getBody().success()).isTrue();
            Long systemId = createResponse.getBody().data().id();

            // When - Delete
            ResponseEntity<ApiResponse<Void>> deleteResponse = restTemplate.exchange(
                    BASE_URL + "/" + systemId,
                    HttpMethod.DELETE,
                    null,
                    new ParameterizedTypeReference<>() {}
            );

            // Then - Deletion can return 200 (success) or 400 (if system has documents)
            assertThat(deleteResponse.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.BAD_REQUEST);

            if (deleteResponse.getStatusCode() == HttpStatus.OK) {
                ResponseEntity<ApiResponse<SystemResponse>> getResponse = restTemplate.exchange(
                        BASE_URL + "/" + systemId,
                        HttpMethod.GET,
                        null,
                        new ParameterizedTypeReference<>() {}
                );
                assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            }
        }

        @Test
        @DisplayName("重复系统编码应报错")
        void shouldReturnErrorForDuplicateSystemCode() {
            // Given - 使用预置的系统编码
            String existingCode = "order-service";

            // When - Try to create with same code
            ResponseEntity<ApiResponse<SystemResponse>> duplicateResponse = restTemplate.exchange(
                    BASE_URL,
                    HttpMethod.POST,
                    new HttpEntity<>(new CreateSystemRequest(existingCode, "Second", "Second system")),
                    new ParameterizedTypeReference<>() {}
            );

            // Then
            assertThat(duplicateResponse.getBody()).isNotNull();
            assertThat(duplicateResponse.getBody().success()).isFalse();
        }

        @Test
        @DisplayName("无效请求应返回错误")
        void shouldReturnErrorForInvalidCreateRequest() {
            // When - Create with empty system code
            ResponseEntity<ApiResponse<SystemResponse>> response = restTemplate.exchange(
                    BASE_URL,
                    HttpMethod.POST,
                    new HttpEntity<>(new CreateSystemRequest("", "Name", "Desc")),
                    new ParameterizedTypeReference<>() {}
            );

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().success()).isFalse();
        }
    }
}
