package io.github.xiaoailazy.coexistree.chat.service;

import io.github.xiaoailazy.coexistree.chat.entity.MessageEntity;

import java.util.List;

/**
 * Service for message storage and retrieval
 */
public interface MessageService {
    
    /**
     * Save a user message with message limit check
     * @param conversationId the conversation ID
     * @param content the message content
     * @return saved message entity
     * @throws io.github.xiaoailazy.coexistree.shared.exception.BusinessException if message limit exceeded
     */
    MessageEntity saveUserMessage(String conversationId, String content);
    
    /**
     * Save an assistant message
     * @param conversationId the conversation ID
     * @param content the message content
     * @param thinking the thinking process (optional)
     * @return saved message entity
     */
    MessageEntity saveAssistantMessage(String conversationId, String content, String thinking);
    
    /**
     * Get all messages for a conversation
     * @param conversationId the conversation ID
     * @return list of messages
     */
    List<MessageEntity> getMessages(String conversationId);
}
