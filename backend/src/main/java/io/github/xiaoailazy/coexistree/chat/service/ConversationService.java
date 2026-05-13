package io.github.xiaoailazy.coexistree.chat.service;

import io.github.xiaoailazy.coexistree.chat.dto.ConversationResponse;
import io.github.xiaoailazy.coexistree.chat.dto.MessageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface ConversationService {

    ConversationResponse createConversation(Long systemId, String title);

    List<ConversationResponse> listConversations();
    
    /**
     * List conversations with pagination support
     * @param systemId the system ID
     * @param pageable pagination parameters
     * @return page of conversations
     */
    Page<ConversationResponse> listConversations(Long systemId, Pageable pageable);

    ConversationResponse getConversation(String conversationId);

    List<MessageResponse> getMessages(String conversationId);

    void deleteConversation(String conversationId);

    String generateTitle(String conversationId);

    void updateTitle(String conversationId, String title);
}
