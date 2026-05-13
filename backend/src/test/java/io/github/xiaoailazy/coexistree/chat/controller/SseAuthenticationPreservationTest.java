package io.github.xiaoailazy.coexistree.chat.controller;

import io.github.xiaoailazy.coexistree.security.jwt.JwtUtil;
import io.github.xiaoailazy.coexistree.user.entity.UserEntity;
import io.github.xiaoailazy.coexistree.user.entity.UserRole;
import io.github.xiaoailazy.coexistree.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Preservation Property Tests for SSE Authentication 401 Bugfix
 * 
 * **Validates: Requirements 3.1, 3.2, 3.3, 3.4**
 * 
 * <h3>Property 2: Preservation - Non-SSE Endpoints Behavior Unchanged</h3>
 * 
 * <p><strong>IMPORTANT:</strong> Follow observation-first methodology</p>
 * 
 * <p>These tests observe behavior on UNFIXED code for non-buggy inputs:</p>
 * <ul>
 *   <li>GET /api/v1/conversations with valid JWT returns 200 OK</li>
 *   <li>GET /api/v1/systems with valid JWT returns 200 OK</li>
 *   <li>Any endpoint without JWT returns 401</li>
 *   <li>Any endpoint with invalid JWT returns 401</li>
 * </ul>
 * 
 * <p><strong>EXPECTED OUTCOME ON UNFIXED CODE:</strong> Tests PASS (this confirms baseline behavior to preserve)</p>
 * 
 * <h3>Preservation Goal</h3>
 * <pre>
 * FOR ALL X WHERE NOT isBugCondition(X) DO
 *   ASSERT F(X) = F'(X)
 * END FOR
 * </pre>
 * 
 * <p>Where:</p>
 * <ul>
 *   <li>F = original (unfixed) request handling function</li>
 *   <li>F' = fixed request handling function</li>
 *   <li>isBugCondition(X) = X is SSE endpoint with valid JWT</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SseAuthenticationPreservationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserRepository userRepository;

    private UserEntity testUser;
    private String validJwtToken;

    @BeforeEach
    void setupTestUser() {
        // Clear SecurityContext before each test
        SecurityContextHolder.clearContext();

        // Create a test user in the database
        testUser = new UserEntity();
        testUser.setUsername("testuser-preservation-" + System.currentTimeMillis());
        testUser.setDisplayName("Test User Preservation");
        testUser.setPasswordHash("test-password");
        testUser.setRole(UserRole.USER);
        testUser = userRepository.save(testUser);

        // Generate a valid JWT token for this user
        validJwtToken = jwtUtil.generateToken(testUser.getId(), testUser.getUsername(), testUser.getRole());
    }

    /**
     * Property 2.1: Non-SSE GET Endpoints With Valid JWT Return 200
     * 
     * **Validates: Requirements 3.1, 3.2**
     * 
     * <p>For all non-SSE GET endpoints with valid JWT: status code = 200</p>
     * 
     * <p><strong>EXPECTED OUTCOME:</strong> Tests PASS on unfixed code (confirms baseline behavior)</p>
     */
    @ParameterizedTest
    @ValueSource(strings = {
        "/api/v1/conversations",
        "/api/v1/systems",
        "/api/v1/auth/me"
    })
    @DisplayName("Property 2.1: Non-SSE GET endpoints with valid JWT should return 200 OK")
    void nonSseGetEndpointsWithValidJwtShouldReturn200(String endpoint) throws Exception {
        // Act: Call non-SSE GET endpoint with valid JWT token
        mockMvc.perform(get(endpoint)
                .header("Authorization", "Bearer " + validJwtToken)
                .with(csrf()))
                // Assert: Should return 200 OK (baseline behavior to preserve)
                .andExpect(status().isOk());
    }

    /**
     * Property 2.2: All GET Endpoints Without JWT Return 401
     * 
     * **Validates: Requirement 3.3**
     * 
     * <p>For all GET endpoints without JWT: status code = 401</p>
     * 
     * <p><strong>EXPECTED OUTCOME:</strong> Tests PASS on unfixed code (confirms baseline behavior)</p>
     */
    @ParameterizedTest
    @ValueSource(strings = {
        "/api/v1/conversations",
        "/api/v1/systems",
        "/api/v1/auth/me",
        "/api/v1/users"
    })
    @DisplayName("Property 2.2: All GET endpoints without JWT should return 401 Unauthorized")
    void allGetEndpointsWithoutJwtShouldReturn401(String endpoint) throws Exception {
        // Act: Call endpoint WITHOUT JWT token
        mockMvc.perform(get(endpoint)
                .with(csrf()))
                // Assert: Should return 401 Unauthorized (baseline behavior to preserve)
                .andExpect(status().isUnauthorized());
    }

    /**
     * Property 2.3: All GET Endpoints With Invalid JWT Return 401
     * 
     * **Validates: Requirement 3.4**
     * 
     * <p>For all GET endpoints with invalid JWT: status code = 401</p>
     * 
     * <p><strong>EXPECTED OUTCOME:</strong> Tests PASS on unfixed code (confirms baseline behavior)</p>
     */
    @Test
    @DisplayName("Property 2.3: All GET endpoints with invalid JWT should return 401 Unauthorized")
    void allGetEndpointsWithInvalidJwtShouldReturn401() throws Exception {
        String[] endpoints = {
            "/api/v1/conversations",
            "/api/v1/systems",
            "/api/v1/auth/me",
            "/api/v1/users"
        };

        String[] invalidTokens = {
            "invalid-token",
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.invalid.signature",
            "",
            "malformed.jwt.token"
        };

        // Test multiple combinations of endpoints and invalid tokens
        for (String endpoint : endpoints) {
            for (String invalidToken : invalidTokens) {
                // Act: Call endpoint with INVALID JWT token
                mockMvc.perform(get(endpoint)
                        .header("Authorization", "Bearer " + invalidToken)
                        .with(csrf()))
                        // Assert: Should return 401 Unauthorized (baseline behavior to preserve)
                        .andExpect(status().isUnauthorized());
            }
        }
    }

    /**
     * Property 2.4: Non-SSE POST Endpoints With Valid JWT Return Success
     * 
     * **Validates: Requirements 3.1, 3.2**
     * 
     * <p>For non-SSE POST endpoints with valid JWT: status code = 200 or 201 or 400 (but not 401)</p>
     * 
     * <p>Note: Some endpoints may return 400 for invalid request body, but should not return 401 
     * when a valid JWT is provided.</p>
     * 
     * <p><strong>EXPECTED OUTCOME:</strong> Tests PASS on unfixed code (confirms baseline behavior)</p>
     */
    @Test
    @DisplayName("Property 2.4: Non-SSE POST endpoints with valid JWT should not return 401")
    void nonSsePostEndpointsWithValidJwtShouldNotReturn401() throws Exception {
        // Test case 1: POST /api/v1/conversations
        mockMvc.perform(post("/api/v1/conversations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"systemId\":1,\"title\":\"Test Conversation\"}")
                .header("Authorization", "Bearer " + validJwtToken)
                .with(csrf()))
                // Should not return 401 (may return 200, 201, or 400 for invalid body)
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    if (status == 401) {
                        throw new AssertionError(
                            "Expected non-401 status for POST /api/v1/conversations with valid JWT, but got 401"
                        );
                    }
                });

        // Test case 2: POST /api/v1/systems
        mockMvc.perform(post("/api/v1/systems")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Test System\",\"description\":\"Test Description\"}")
                .header("Authorization", "Bearer " + validJwtToken)
                .with(csrf()))
                // Should not return 401 (may return 200, 201, or 400 for invalid body)
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    if (status == 401) {
                        throw new AssertionError(
                            "Expected non-401 status for POST /api/v1/systems with valid JWT, but got 401"
                        );
                    }
                });
    }

    /**
     * Property 2.5: POST Endpoints Without JWT Return 401
     * 
     * **Validates: Requirement 3.3**
     * 
     * <p>For POST endpoints without JWT: status code = 401</p>
     * 
     * <p><strong>EXPECTED OUTCOME:</strong> Tests PASS on unfixed code (confirms baseline behavior)</p>
     */
    @Test
    @DisplayName("Property 2.5: POST endpoints without JWT should return 401 Unauthorized")
    void postEndpointsWithoutJwtShouldReturn401() throws Exception {
        // Test case 1: POST /api/v1/conversations without JWT
        mockMvc.perform(post("/api/v1/conversations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"systemId\":1,\"title\":\"Test\"}")
                .with(csrf()))
                .andExpect(status().isUnauthorized());

        // Test case 2: POST /api/v1/systems without JWT
        mockMvc.perform(post("/api/v1/systems")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Test\",\"description\":\"Test\"}")
                .with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Property 2.6: POST Endpoints With Invalid JWT Return 401
     * 
     * **Validates: Requirement 3.4**
     * 
     * <p>For POST endpoints with invalid JWT: status code = 401</p>
     * 
     * <p><strong>EXPECTED OUTCOME:</strong> Tests PASS on unfixed code (confirms baseline behavior)</p>
     */
    @Test
    @DisplayName("Property 2.6: POST endpoints with invalid JWT should return 401 Unauthorized")
    void postEndpointsWithInvalidJwtShouldReturn401() throws Exception {
        String[] invalidTokens = {
            "invalid-token",
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.invalid.signature",
            "malformed.jwt.token"
        };

        for (String invalidToken : invalidTokens) {
            // Test case 1: POST /api/v1/conversations with invalid JWT
            mockMvc.perform(post("/api/v1/conversations")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"systemId\":1,\"title\":\"Test\"}")
                    .header("Authorization", "Bearer " + invalidToken)
                    .with(csrf()))
                    .andExpect(status().isUnauthorized());

            // Test case 2: POST /api/v1/systems with invalid JWT
            mockMvc.perform(post("/api/v1/systems")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"Test\",\"description\":\"Test\"}")
                    .header("Authorization", "Bearer " + invalidToken)
                    .with(csrf()))
                    .andExpect(status().isUnauthorized());
        }
    }
}
