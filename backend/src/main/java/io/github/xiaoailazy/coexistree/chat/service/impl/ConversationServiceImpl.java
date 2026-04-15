package io.github.xiaoailazy.coexistree.chat.service.impl;

import io.github.xiaoailazy.coexistree.chat.dto.ChatRequest;
import io.github.xiaoailazy.coexistree.chat.dto.ConversationResponse;
import io.github.xiaoailazy.coexistree.chat.dto.MessageResponse;
import io.github.xiaoailazy.coexistree.chat.entity.ConversationEntity;
import io.github.xiaoailazy.coexistree.chat.repository.ConversationRepository;
import io.github.xiaoailazy.coexistree.chat.repository.MessageRepository;
import io.github.xiaoailazy.coexistree.chat.service.ConversationService;
import io.github.xiaoailazy.coexistree.security.model.SecurityUserDetails;
import io.github.xiaoailazy.coexistree.shared.enums.ErrorCode;
import io.github.xiaoailazy.coexistree.shared.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class ConversationServiceImpl implements ConversationService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;

    public ConversationServiceImpl(
            ConversationRepository conversationRepository,
            MessageRepository messageRepository
    ) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
    }

    @Override
    @Transactional
    public ConversationResponse createConversation(Long systemId, String title) {
        ConversationEntity entity = new ConversationEntity();
        entity.setConversationId(UUID.randomUUID().toString());
        entity.setSystemId(systemId);
        entity.setTitle(title != null ? title : "New Conversation");
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        entity = conversationRepository.save(entity);
        return toResponse(entity);
    }

    @Override
    public List<ConversationResponse> listConversations() {
        return conversationRepository.findAllByOrderByUpdatedAtDesc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public ConversationResponse getConversation(String conversationId) {
        return conversationRepository.findByConversationId(conversationId)
                .map(this::toResponse)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONVERSATION_NOT_FOUND, "会话不存在: " + conversationId));
    }

    @Override
    public List<MessageResponse> getMessages(String conversationId) {
        return messageRepository.findByConversationIdOrderByCreatedAt(conversationId)
                .stream()
                .map(m -> new MessageResponse(
                        m.getId(),
                        m.getRole(),
                        m.getContent(),
                        m.getThinking(),
                        null,
                        m.getCreatedAt()
                ))
                .toList();
    }

    @Override
    @Transactional
    public void deleteConversation(String conversationId) {
        messageRepository.deleteByConversationId(conversationId);
        conversationRepository.findByConversationId(conversationId)
                .ifPresent(conversationRepository::delete);
    }

    @Override
    public String generateTitle(String conversationId) {
        // Simple default: return existing title or a placeholder
        return conversationRepository.findByConversationId(conversationId)
                .map(ConversationEntity::getTitle)
                .orElse("Untitled");
    }

    @Override
    public void updateTitle(String conversationId, String title) {
        conversationRepository.findByConversationId(conversationId)
                .ifPresent(entity -> {
                    entity.setTitle(title);
                    entity.setUpdatedAt(LocalDateTime.now());
                    conversationRepository.save(entity);
                });
    }

    @Override
    public void smartChatStream(String conversationId, ChatRequest request, SseEmitter emitter, SecurityUserDetails userDetails) {
        // Delegate to AgentChatService via the controller's direct endpoint
        // This legacy method is kept for compatibility but should not be called directly
        try {
            emitter.send(SseEmitter.event().data("This endpoint is deprecated. Use /smart-chat instead."));
        } catch (java.io.IOException ignored) {}
        emitter.complete();
    }

    private ConversationResponse toResponse(ConversationEntity entity) {
        return new ConversationResponse(
                entity.getConversationId(),
                entity.getSystemId(),
                entity.getTitle(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
