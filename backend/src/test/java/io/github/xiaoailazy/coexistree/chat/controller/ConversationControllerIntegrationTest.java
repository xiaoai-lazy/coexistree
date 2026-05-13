package io.github.xiaoailazy.coexistree.chat.controller;

import io.github.xiaoailazy.coexistree.chat.dto.ConversationResponse;
import io.github.xiaoailazy.coexistree.chat.service.ConversationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ConversationControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ConversationService conversationService;

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void shouldCreateConversation() throws Exception {
        String conversationId = UUID.randomUUID().toString();
        ConversationResponse response = new ConversationResponse(
                conversationId, 1L, "Test Conversation", LocalDateTime.now(), LocalDateTime.now()
        );

        when(conversationService.createConversation(eq(1L), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/conversations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "systemId": 1,
                                    "title": "Test Conversation"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.conversationId").value(conversationId));
    }

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void shouldCreateConversationWithoutTitle() throws Exception {
        String conversationId = UUID.randomUUID().toString();
        ConversationResponse response = new ConversationResponse(
                conversationId, 1L, null, LocalDateTime.now(), LocalDateTime.now()
        );

        when(conversationService.createConversation(eq(1L), eq(null))).thenReturn(response);

        mockMvc.perform(post("/api/v1/conversations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "systemId": 1
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void shouldListConversations() throws Exception {
        String id1 = UUID.randomUUID().toString();
        String id2 = UUID.randomUUID().toString();

        ConversationResponse conv1 = new ConversationResponse(
                id1, 1L, "会话1", LocalDateTime.now(), LocalDateTime.now()
        );
        ConversationResponse conv2 = new ConversationResponse(
                id2, 1L, "会话2", LocalDateTime.now(), LocalDateTime.now()
        );

        when(conversationService.listConversations()).thenReturn(List.of(conv1, conv2));

        mockMvc.perform(get("/api/v1/conversations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(2)));
    }

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void shouldGetConversationById() throws Exception {
        String conversationId = UUID.randomUUID().toString();
        ConversationResponse response = new ConversationResponse(
                conversationId, 1L, "测试会话", LocalDateTime.now(), LocalDateTime.now()
        );

        when(conversationService.getConversation(conversationId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/conversations/{conversationId}", conversationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.conversationId").value(conversationId));
    }

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void shouldGetMessagesForConversation() throws Exception {
        String conversationId = UUID.randomUUID().toString();

        when(conversationService.getMessages(conversationId)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/conversations/{conversationId}/messages", conversationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void shouldDeleteConversation() throws Exception {
        String conversationId = UUID.randomUUID().toString();
        doNothing().when(conversationService).deleteConversation(conversationId);

        mockMvc.perform(delete("/api/v1/conversations/{conversationId}", conversationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void shouldReturnErrorForNonExistentConversation() throws Exception {
        String conversationId = "non-existent-id";
        when(conversationService.getConversation(conversationId))
                .thenThrow(new io.github.xiaoailazy.coexistree.shared.exception.BusinessException(
                        io.github.xiaoailazy.coexistree.shared.enums.ErrorCode.CONVERSATION_NOT_FOUND, "Not found"));

        mockMvc.perform(get("/api/v1/conversations/{conversationId}", conversationId))
                .andExpect(status().isBadRequest());
    }
}
