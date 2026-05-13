package io.github.xiaoailazy.coexistree.chat.controller;

import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import com.google.adk.models.langchain4j.LangChain4j;
import io.github.xiaoailazy.coexistree.security.model.SecurityUserDetails;
import io.github.xiaoailazy.coexistree.user.entity.UserEntity;
import io.github.xiaoailazy.coexistree.user.entity.UserRole;
import io.reactivex.rxjava3.core.Flowable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression test for the 401 Unauthorized issue on the /smart-chat SSE endpoint.
 *
 * <h3>Root Cause</h3>
 * <p>Spring Security's {@code SecurityContextHolder} defaults to {@code MODE_THREADLOCAL}.
 * When an SSE endpoint triggers async request dispatch, Spring MVC processes the
 * dispatch on a different thread. With {@code MODE_THREADLOCAL}, the
 * {@code SecurityContext} set by {@code JwtAuthenticationFilter} on the main thread
 * is NOT visible to the async dispatch thread. The {@code AuthorizationFilter} then
 * sees no authentication and returns 401.</p>
 *
 * <h3>Fix</h3>
 * <p>Explicitly save and restore {@code SecurityContext} via request attribute
 * ({@code "SPRING_SECURITY_CONTEXT"}) in {@code JwtAuthenticationFilter}.
 * During the initial request, the filter sets authentication and stores the context
 * as a request attribute. During async dispatch, the filter detects the
 * {@code DispatcherType.ASYNC} and restores the context from that attribute.
 * This approach is more reliable than relying on thread-local propagation across
 * async boundaries, and maintains the default {@code MODE_THREADLOCAL} isolation.</p>
 *
 * <p>See: {@code docs/troubleshooting/docker-chat-401.md}</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SmartChatSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    /**
     * Mock only the LLM layer, not the AgentChatService.
     * This lets the real Google ADK Agent framework (InMemoryRunner, agent tool loop,
     * event streaming) run end-to-end, but without making real LLM API calls.
     *
     * The mock emits a simple text response with turnComplete, so the agent loop
     * terminates immediately without invoking any tools.
     */
    @MockitoBean
    private LangChain4j adkLlm;

    /**
     * Creates a mock SecurityUserDetails for testing.
     * Cannot use @WithMockUser because the controller expects SecurityUserDetails specifically.
     */
    private static SecurityUserDetails createTestUser() {
        UserEntity user = new UserEntity();
        user.setId(1L);
        user.setUsername("testuser");
        user.setPasswordHash("test-password");
        user.setRole(UserRole.USER);
        return new SecurityUserDetails(user);
    }

    private static LlmResponse mockLlmResponse(String text) {
        return LlmResponse.builder()
                .content(Content.fromParts(Part.fromText(text)))
                .turnComplete(true)
                .build();
    }

    @Nested
    @DisplayName("SecurityContext Request Attribute Propagation")
    class SecurityContextHolderStrategyTest {

        @Test
        @DisplayName("should use MODE_THREADLOCAL (SecurityContext isolated per thread)")
        void shouldUseThreadLocalStrategy() {
            // With MODE_THREADLOCAL, a child thread should NOT inherit the parent's SecurityContext.
            // This confirms the strategy hasn't been accidentally changed to INHERITABLETHREADLOCAL.
            SecurityContextHolder.getContext().setAuthentication(
                    new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                            "test", null, java.util.List.of()
                    )
            );

            var capturedAuth = new java.util.concurrent.atomic.AtomicReference<>();
            Thread childThread = new Thread(() -> {
                capturedAuth.set(SecurityContextHolder.getContext().getAuthentication());
            });
            childThread.start();
            try {
                childThread.join(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            assertThat(capturedAuth.get())
                    .as("SecurityContext should NOT propagate to child threads (MODE_THREADLOCAL)")
                    .isNull();
        }
    }

    @Nested
    @DisplayName("Smart-Chat SSE Endpoint Authentication")
    class SmartChatAuthenticationTest {

        @Test
        @DisplayName("should return 401 when calling smart-chat without authentication")
        void shouldReturn401WithoutAuth() throws Exception {
            mockMvc.perform(post("/api/v1/conversations/conv-1/smart-chat")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"question\":\"hello\"}")
                            .with(csrf()))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().json("{\"success\":false,\"code\":401,\"message\":\"Unauthorized\"}"));
        }

        @Test
        @DisplayName("should run real ADK Agent flow and return SSE response with valid auth")
        void shouldAcceptAuthenticatedRequest() throws Exception {
            // Mock the LLM to return a simple text response with turnComplete.
            // This allows the real ADK Agent framework to run (InMemoryRunner,
            // agent event loop, SSE streaming) without making real API calls.
            // Since turnComplete=true and no functionCalls, the agent terminates
            // immediately without invoking any tools.
            when(adkLlm.generateContent(any(LlmRequest.class), anyBoolean()))
                    .thenReturn(Flowable.just(mockLlmResponse("Mock answer: hello")));

            mockMvc.perform(post("/api/v1/conversations/conv-1/smart-chat")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"question\":\"hello\"}")
                            .with(csrf())
                            .with(user(createTestUser())))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM_VALUE));
        }
    }
}
