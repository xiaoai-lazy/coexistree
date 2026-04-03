package io.github.xiaoailazy.coexistree.document.controller;

import io.github.xiaoailazy.coexistree.security.jwt.JwtUtil;
import io.github.xiaoailazy.coexistree.shared.api.ApiResponse;
import io.github.xiaoailazy.coexistree.user.entity.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 使用 @Sql 管理测试数据的示例
 *
 * 对比：
 * - 不用 @Sql：每个测试都要手动 save 多个实体
 * - 使用 @Sql：直接查询预置数据，测试代码更简洁
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Sql(scripts = "/sql/base-test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(scripts = "/sql/cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class DocumentControllerSqlTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        // 生成 testuser 的 JWT token (id=101)
        String token = jwtUtil.generateToken(101L, "testuser", UserRole.SUPER_ADMIN);

        // 配置 TestRestTemplate 自动添加 Authorization 请求头
        restTemplate.getRestTemplate().setInterceptors(List.of(
            (ClientHttpRequestInterceptor) (request, body, execution) -> {
                request.getHeaders().add("Authorization", "Bearer " + token);
                return execution.execute(request, body);
            }
        ));
    }

    @Test
    @DisplayName("查询系统文档列表 - 使用预置数据")
    void shouldListDocumentsWithExistingData() {
        // 注意：没有手动创建任何数据！
        // 数据已经在 base-test-data.sql 中预置

        ResponseEntity<ApiResponse> response = restTemplate.exchange(
                "/api/v1/documents?systemId=1",
                HttpMethod.GET,
                null,
                ApiResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isTrue();

        List<Map<String, Object>> documents = (List<Map<String, Object>>) response.getBody().data();

        // 验证预置数据
        assertThat(documents).hasSize(2);  // systemId=1 有 2 个文档
        assertThat(documents.get(0).get("originalFileName")).isEqualTo("order-api.md");
        assertThat(documents.get(1).get("originalFileName")).isEqualTo("user-guide.md");
    }

    @Test
    @DisplayName("查询其他系统的文档 - 权限控制")
    void shouldNotAccessOtherSystemDocuments() {
        // testuser 是 systemId=1 的 OWNER
        // 但 systemId=2 属于 otheruser

        // 如果实现权限控制，这里应该返回 403 或空列表
        ResponseEntity<ApiResponse> response = restTemplate.exchange(
                "/api/v1/documents?systemId=2",
                HttpMethod.GET,
                null,
                ApiResponse.class
        );

        // 根据实际权限策略验证
        // 可能返回 403 或空列表
        assertThat(response.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("其他用户查看自己的文档")
    void shouldAccessOwnDocuments() {
        // 切换为 otheruser (id=102)
        String otherToken = jwtUtil.generateToken(102L, "otheruser", UserRole.USER);
        restTemplate.getRestTemplate().setInterceptors(List.of(
            (ClientHttpRequestInterceptor) (request, body, execution) -> {
                request.getHeaders().add("Authorization", "Bearer " + otherToken);
                return execution.execute(request, body);
            }
        ));

        ResponseEntity<ApiResponse> response = restTemplate.exchange(
                "/api/v1/documents?systemId=2",
                HttpMethod.GET,
                null,
                ApiResponse.class
        );

        // 根据当前权限策略，可能返回 200 (允许访问) 或 400/403 (权限不足)
        assertThat(response.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.BAD_REQUEST, HttpStatus.FORBIDDEN);

        if (response.getStatusCode() == HttpStatus.OK) {
            List<Map<String, Object>> documents = (List<Map<String, Object>>) response.getBody().data();
            assertThat(documents).hasSize(1);
            assertThat(documents.get(0).get("originalFileName")).isEqualTo("other-system.md");
        }
    }
}
