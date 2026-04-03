package io.github.xiaoailazy.coexistree.chat.controller;

import io.github.xiaoailazy.coexistree.chat.dto.ConversationResponse;
import io.github.xiaoailazy.coexistree.chat.dto.MessageResponse;
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
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ConversationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ConversationService conversationService;

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void testCreateConversation() throws Exception {
        // Given
        String conversationId = UUID.randomUUID().toString();
        ConversationResponse response = new ConversationResponse(
                conversationId, 1L, null, LocalDateTime.now(), LocalDateTime.now()
        );

        when(conversationService.createConversation(eq(1L), isNull())).thenReturn(response);

        // When & Then
        mockMvc.perform(post("/api/v1/conversations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "systemId": 1
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.conversationId").value(conversationId))
                .andExpect(jsonPath("$.data.systemId").value(1));
    }

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void testCreateConversationWithTitle() throws Exception {
        // Given
        String conversationId = UUID.randomUUID().toString();
        ConversationResponse response = new ConversationResponse(
                conversationId, 1L, "测试会话", LocalDateTime.now(), LocalDateTime.now()
        );

        when(conversationService.createConversation(eq(1L), eq("测试会话"))).thenReturn(response);

        // When & Then
        mockMvc.perform(post("/api/v1/conversations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "systemId": 1,
                                    "title": "测试会话"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("测试会话"));
    }

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void testListConversations() throws Exception {
        // Given
        String id1 = UUID.randomUUID().toString();
        String id2 = UUID.randomUUID().toString();

        ConversationResponse conv1 = new ConversationResponse(
                id1, 1L, "会话1", LocalDateTime.now(), LocalDateTime.now()
        );
        ConversationResponse conv2 = new ConversationResponse(
                id2, 1L, "会话2", LocalDateTime.now(), LocalDateTime.now()
        );

        when(conversationService.listConversations()).thenReturn(List.of(conv1, conv2));

        // When & Then
        mockMvc.perform(get("/api/v1/conversations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].title").value("会话1"))
                .andExpect(jsonPath("$.data[1].title").value("会话2"));
    }

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void testGetConversation() throws Exception {
        // Given
        String conversationId = UUID.randomUUID().toString();
        ConversationResponse response = new ConversationResponse(
                conversationId, 1L, "测试会话", LocalDateTime.now(), LocalDateTime.now()
        );

        when(conversationService.getConversation(conversationId)).thenReturn(response);

        // When & Then
        mockMvc.perform(get("/api/v1/conversations/{conversationId}", conversationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.conversationId").value(conversationId))
                .andExpect(jsonPath("$.data.title").value("测试会话"));
    }

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void testGetMessages() throws Exception {
        // Given
        String conversationId = UUID.randomUUID().toString();
        MessageResponse msg1 = new MessageResponse(1L, "USER", "问题", null, null, LocalDateTime.now());
        MessageResponse msg2 = new MessageResponse(2L, "ASSISTANT", "回答", null, null, LocalDateTime.now());

        when(conversationService.getMessages(conversationId)).thenReturn(List.of(msg1, msg2));

        // When & Then
        mockMvc.perform(get("/api/v1/conversations/{conversationId}/messages", conversationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].role").value("USER"))
                .andExpect(jsonPath("$.data[0].content").value("问题"))
                .andExpect(jsonPath("$.data[1].role").value("ASSISTANT"))
                .andExpect(jsonPath("$.data[1].content").value("回答"));
    }

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void testDeleteConversation() throws Exception {
        // Given
        String conversationId = UUID.randomUUID().toString();
        doNothing().when(conversationService).deleteConversation(conversationId);

        // When & Then
        mockMvc.perform(delete("/api/v1/conversations/{conversationId}", conversationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void testGenerateTitle() throws Exception {
        // Given
        String conversationId = UUID.randomUUID().toString();
        when(conversationService.generateTitle(conversationId)).thenReturn("生成的标题");

        // When & Then
        mockMvc.perform(post("/api/v1/conversations/{conversationId}/title", conversationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data").value("生成的标题"));
    }

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void testListEmptyConversations() throws Exception {
        // Given
        when(conversationService.listConversations()).thenReturn(List.of());

        // When & Then
        mockMvc.perform(get("/api/v1/conversations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }
}
