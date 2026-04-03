package io.github.xiaoailazy.coexistree.chat.service;

import io.github.xiaoailazy.coexistree.chat.dto.ChatRequest;
import io.github.xiaoailazy.coexistree.chat.dto.ConversationResponse;
import io.github.xiaoailazy.coexistree.chat.dto.MessageResponse;
import io.github.xiaoailazy.coexistree.security.model.SecurityUserDetails;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

public interface ConversationService {

    ConversationResponse createConversation(Long systemId, String title);

    List<ConversationResponse> listConversations();

    ConversationResponse getConversation(String conversationId);

    List<MessageResponse> getMessages(String conversationId);

    void deleteConversation(String conversationId);

    /**
     * 智能对话流（支持意图识别和需求评估）
     *
     * @param conversationId 会话ID
     * @param request        对话请求（包含问题和可选的文档ID）
     * @param emitter        SSE发射器
     * @param userDetails    当前用户详情
     */
    void smartChatStream(String conversationId, ChatRequest request, SseEmitter emitter, SecurityUserDetails userDetails);

    String generateTitle(String conversationId);

    void updateTitle(String conversationId, String title);
}
