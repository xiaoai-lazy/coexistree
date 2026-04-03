package io.github.xiaoailazy.coexistree.shared.test;

import io.github.xiaoailazy.coexistree.security.jwt.JwtUtil;
import io.github.xiaoailazy.coexistree.user.entity.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import java.util.List;

/**
 * 集成测试基类，提供预置测试数据和 JWT 认证。
 *
 * <p>功能：
 * <ul>
 *   <li>自动导入基础测试数据（用户、系统、文档）</li>
 *   <li>自动配置 JWT 认证（testuser）</li>
 *   <li>提供 TestRestTemplate</li>
 * </ul>
 *
 * <p>预置数据（来自 base-test-data.sql）：
 * <ul>
 *   <li>用户: testuser(id=101), otheruser(id=102), admin(id=199)</li>
 *   <li>系统: order-service(id=1, owner=testuser), user-service(id=2, owner=otheruser)</li>
 *   <li>文档: system1 有 2 个文档，system2 有 1 个文档</li>
 *   <li>成员: testuser 是 system1 的 OWNER</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>
 * class MyTest extends BaseDataIntegrationTest {
 *     @Test
 *     void shouldWork() {
 *         // 直接测试，数据已准备好
 *         ResponseEntity&lt;ApiResponse&gt; response = restTemplate.getForEntity("/api/v1/systems", ...);
 *     }
 * }
 * </pre>
 *
 * <p>需要额外数据时，可叠加 @Sql 注解：
 * <pre>
 * @Sql(scripts = "/sql/extra-data.sql", executionPhase = BEFORE_TEST_CLASS)
 * class MyTest extends BaseDataIntegrationTest { ... }
 * </pre>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Sql(scripts = "/sql/base-test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(scripts = "/sql/cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
public abstract class BaseDataIntegrationTest {

    @Autowired
    protected TestRestTemplate restTemplate;

    @Autowired
    private JwtUtil jwtUtil;

    protected String testToken;

    /**
     * 测试前设置：生成 JWT Token 并配置到 RestTemplate
     *
     * <p>使用 testuser (id=1, role=USER) 的身份进行认证
     */
    @BeforeEach
    void setUpAuth() {
        // 生成 testuser 的 JWT token (id=101，与 base-test-data.sql 对应)
        testToken = jwtUtil.generateToken(101L, "testuser", UserRole.SUPER_ADMIN);

        // 配置 TestRestTemplate 自动添加 Authorization 请求头
        restTemplate.getRestTemplate().setInterceptors(List.of(
            (ClientHttpRequestInterceptor) (request, body, execution) -> {
                request.getHeaders().add("Authorization", "Bearer " + testToken);
                return execution.execute(request, body);
            }
        ));
    }

    /**
     * 生成指定用户的 JWT Token
     *
     * @param userId   用户ID
     * @param username 用户名
     * @param role     用户角色
     * @return JWT Token 字符串
     */
    protected String generateToken(Long userId, String username, UserRole role) {
        return jwtUtil.generateToken(userId, username, role);
    }

    /**
     * 切换当前请求使用的用户身份
     *
     * @param userId   用户ID
     * @param username 用户名
     * @param role     用户角色
     */
    protected void switchUser(Long userId, String username, UserRole role) {
        String newToken = jwtUtil.generateToken(userId, username, role);
        restTemplate.getRestTemplate().setInterceptors(List.of(
            (ClientHttpRequestInterceptor) (request, body, execution) -> {
                request.getHeaders().add("Authorization", "Bearer " + newToken);
                return execution.execute(request, body);
            }
        ));
    }

    /**
     * 重置为默认 testuser 身份
     */
    protected void resetToDefaultUser() {
        setUpAuth();
    }
}
