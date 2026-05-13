package io.github.xiaoailazy.coexistree.chat.service.impl;

import io.github.xiaoailazy.coexistree.chat.entity.ConversationEntity;
import io.github.xiaoailazy.coexistree.chat.entity.MessageEntity;
import io.github.xiaoailazy.coexistree.chat.repository.ConversationRepository;
import io.github.xiaoailazy.coexistree.chat.repository.MessageRepository;
import io.github.xiaoailazy.coexistree.chat.service.MessageService;
import io.github.xiaoailazy.coexistree.shared.enums.ErrorCode;
import io.github.xiaoailazy.coexistree.shared.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class MessageServiceImpl implements MessageService {
    
    private static final int MAX_MESSAGES_PER_CONVERSATION = 200;
    
    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    
    public MessageServiceImpl(
            MessageRepository messageRepository,
            ConversationRepository conversationRepository
    ) {
        this.messageRepository = messageRepository;
        this.conversationRepository = conversationRepository;
    }
    
    @Override
    @Transactional
    public MessageEntity saveUserMessage(String conversationId, String content) {
        // Check if conversation exists
        ConversationEntity conversation = conversationRepository
                .findByConversationId(conversationId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.CONVERSATION_NOT_FOUND,
                        "会话不存在: " + conversationId
                ));
        
        // Check message count limit
        if (messageRepository.countByConversationId(conversationId) >= MAX_MESSAGES_PER_CONVERSATION) {
            throw new BusinessException(
                    ErrorCode.MESSAGE_LIMIT_EXCEEDED,
                    String.format("会话已达到%d条消息上限，请创建新会话继续对话", 
                            MAX_MESSAGES_PER_CONVERSATION)
            );
        }
        
        // Save message
        MessageEntity message = new MessageEntity();
        message.setConversationId(conversationId);
        message.setRole("USER");
        message.setContent(content);
        message.setCreatedAt(LocalDateTime.now());
        message = messageRepository.save(message);

        // Update conversation timestamp
        conversation.setUpdatedAt(LocalDateTime.now());
        conversationRepository.save(conversation);
        
        return message;
    }
    
    @Override
    @Transactional
    public MessageEntity saveAssistantMessage(String conversationId, String content, String thinking) {
        // Check if conversation exists
        ConversationEntity conversation = conversationRepository
                .findByConversationId(conversationId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.CONVERSATION_NOT_FOUND,
                        "会话不存在: " + conversationId
                ));
        
        // Save message
        MessageEntity message = new MessageEntity();
        message.setConversationId(conversationId);
        message.setRole("ASSISTANT");
        message.setContent(content);
        message.setThinking(thinking);
        message.setCreatedAt(LocalDateTime.now());
        message = messageRepository.save(message);

        // Update conversation timestamp
        conversation.setUpdatedAt(LocalDateTime.now());
        conversationRepository.save(conversation);
        
        return message;
    }
    
    @Override
    public List<MessageEntity> getMessages(String conversationId) {
        return messageRepository.findByConversationIdOrderByCreatedAt(conversationId);
    }
}
