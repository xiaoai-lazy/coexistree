package io.github.xiaoailazy.coexistree.chat.controller;

import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import com.google.adk.models.langchain4j.LangChain4j;
import io.github.xiaoailazy.coexistree.security.jwt.JwtUtil;
import io.github.xiaoailazy.coexistree.user.entity.UserEntity;
import io.github.xiaoailazy.coexistree.user.entity.UserRole;
import io.github.xiaoailazy.coexistree.user.repository.UserRepository;
import io.reactivex.rxjava3.core.Flowable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;

/**
 * Bug Condition Exploration Test for SSE Authentication 401 Bug
 * 
 * **Validates: Requirements 1.1, 1.2, 1.3, 2.1, 2.2, 2.3**
 * 
 * <h3>Property 1: Bug Condition - SSE Endpoint Returns 401 With Valid JWT</h3>
 * 
 * <p><strong>CRITICAL:</strong> This test MUST FAIL on unfixed code - failure confirms the bug exists.</p>
 * 
 * <p><strong>DO NOT attempt to fix the test or the code when it fails.</strong></p>
 * 
 * <p><strong>NOTE:</strong> This test encodes the expected behavior - it will validate the fix 
 * when it passes after implementation.</p>
 * 
 * <p><strong>GOAL:</strong> Surface counterexamples that demonstrate the bug exists.</p>
 * 
 * <h3>Bug Condition Function</h3>
 * <pre>
 * FUNCTION isBugCondition(X)
 *   INPUT: X of type HttpRequest
 *   OUTPUT: boolean
 *   
 *   RETURN (X.endpoint = "/api/v1/conversations/{id}/smart-chat") 
 *          AND (X.method = "POST")
 *          AND (X.hasValidJwtToken = true)
 *          AND (X.responseType = "text/event-stream")
 * END FUNCTION
 * </pre>
 * 
 * <h3>Expected Behavior (After Fix)</h3>
 * <ul>
 *   <li>Response status code should be 200</li>
 *   <li>Response content type should be "text/event-stream"</li>
 *   <li>SecurityContext should be available on async dispatch thread</li>
 * </ul>
 * 
 * <h3>Current Behavior (Bug - EXPECTED TO FAIL)</h3>
 * <ul>
 *   <li>Actual status code: 401</li>
 *   <li>SecurityContext availability: null or missing authentication</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SseAuthenticationBugConditionTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private LangChain4j adkLlm;

    private UserEntity testUser;
    private String validJwtToken;

    @BeforeEach
    void setupTestUser() {
        // Clear SecurityContext before each test
        SecurityContextHolder.clearContext();

        // Create a test user in the database
        testUser = new UserEntity();
        testUser.setUsername("testuser-" + System.currentTimeMillis());
        testUser.setDisplayName("Test User");
        testUser.setPasswordHash("test-password");
        testUser.setRole(UserRole.USER);
        testUser = userRepository.save(testUser);

        // Generate a valid JWT token for this user
        validJwtToken = jwtUtil.generateToken(testUser.getId(), testUser.getUsername(), testUser.getRole());

        // Mock the LLM to return a simple response
        when(adkLlm.generateContent(any(LlmRequest.class), anyBoolean()))
                .thenReturn(Flowable.just(mockLlmResponse("Mock answer")));
    }

    /**
     * Property 1: Bug Condition - SSE Endpoint Returns 401 With Valid JWT
     * 
     * **Validates: Requirements 1.1, 1.2, 1.3, 2.1, 2.2, 2.3**
     * 
     * <p>This property tests the bug condition: SSE endpoint with valid JWT token.</p>
     * 
     * <p><strong>Scoped PBT Approach:</strong> For deterministic bugs, we scope the property 
     * to the concrete failing case(s) to ensure reproducibility.</p>
     * 
     * <p><strong>EXPECTED OUTCOME ON UNFIXED CODE:</strong> Test FAILS (this is correct - it proves the bug exists)</p>
     * 
     * <p><strong>Counterexamples to document:</strong></p>
     * <ul>
     *   <li>Actual status code: 401 (expected: 200)</li>
     *   <li>SecurityContext availability: null or missing authentication</li>
     * </ul>
     */
    @Test
    @DisplayName("Property 1: SSE endpoint with valid JWT should return 200 OK and text/event-stream")
    void sseEndpointWithValidJwtShouldReturn200AndEventStream() throws Exception {
        // Test case 1: Basic conversation ID and question
        testSseEndpointWithValidJwt("test-conv-1", "hello");
        
        // Test case 2: Different conversation ID
        testSseEndpointWithValidJwt("test-conv-2", "test question");
        
        // Test case 3: Non-ASCII characters
        testSseEndpointWithValidJwt("bug-reproduction-conv", "你好");
    }

    private void testSseEndpointWithValidJwt(String conversationId, String question) throws Exception {
        // Arrange: Prepare request with valid JWT token
        String requestBody = String.format("{\"question\":\"%s\"}", question);

        // Act: Call SSE endpoint with valid JWT token
        MvcResult mvcResult = mockMvc.perform(post("/api/v1/conversations/" + conversationId + "/smart-chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)
                        .header("Authorization", "Bearer " + validJwtToken)
                        .with(csrf()))
                .andExpect(request().asyncStarted())
                .andReturn();

        // Complete the async dispatch
        MvcResult asyncResult = mockMvc.perform(asyncDispatch(mvcResult))
                .andReturn();

        // Assert: Expected behavior (will FAIL on unfixed code)
        int actualStatusCode = asyncResult.getResponse().getStatus();
        String actualContentType = asyncResult.getResponse().getContentType();

        // Document counterexamples
        if (actualStatusCode == 401) {
            System.err.println("=== BUG CONDITION DETECTED ===");
            System.err.println("Counterexample found:");
            System.err.println("  conversationId: " + conversationId);
            System.err.println("  question: " + question);
            System.err.println("  Expected status: 200");
            System.err.println("  Actual status: " + actualStatusCode);
            System.err.println("  Expected content-type: text/event-stream");
            System.err.println("  Actual content-type: " + actualContentType);
            System.err.println("  SecurityContext on async thread: likely null or missing authentication");
            System.err.println("=== END COUNTEREXAMPLE ===");
        }

        // These assertions encode the EXPECTED BEHAVIOR (after fix)
        assertThat(actualStatusCode)
                .as("SSE endpoint with valid JWT should return 200 OK (not 401) for conversationId=%s, question=%s", 
                    conversationId, question)
                .isEqualTo(200);

        assertThat(actualContentType)
                .as("SSE endpoint should return text/event-stream content type for conversationId=%s, question=%s", 
                    conversationId, question)
                .contains("text/event-stream");
    }

    private static LlmResponse mockLlmResponse(String text) {
        return LlmResponse.builder()
                .content(Content.fromParts(Part.fromText(text)))
                .turnComplete(true)
                .build();
    }
}
