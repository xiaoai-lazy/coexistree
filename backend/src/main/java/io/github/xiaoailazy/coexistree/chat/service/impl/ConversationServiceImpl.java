package io.github.xiaoailazy.coexistree.chat.service.impl;

import io.github.xiaoailazy.coexistree.agent.service.AgentChatService;
import io.github.xiaoailazy.coexistree.chat.dto.ConversationResponse;
import io.github.xiaoailazy.coexistree.chat.dto.MessageResponse;
import io.github.xiaoailazy.coexistree.chat.entity.ConversationEntity;
import io.github.xiaoailazy.coexistree.chat.repository.ConversationRepository;
import io.github.xiaoailazy.coexistree.chat.service.ConversationService;
import io.github.xiaoailazy.coexistree.chat.service.TitleGenerationService;
import io.github.xiaoailazy.coexistree.observability.entity.ConversationRunEntity;
import io.github.xiaoailazy.coexistree.observability.repository.ConversationRunRepository;
import io.github.xiaoailazy.coexistree.shared.enums.ErrorCode;
import io.github.xiaoailazy.coexistree.shared.exception.BusinessException;
import jakarta.persistence.OptimisticLockException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

@Service
public class ConversationServiceImpl implements ConversationService {

    private static final Logger log = LoggerFactory.getLogger(ConversationServiceImpl.class);
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 100;

    private final ConversationRepository conversationRepository;
    private final ConversationRunRepository conversationRunRepository;
    private final AgentChatService agentChatService;
    private final TitleGenerationService titleGenerationService;

    public ConversationServiceImpl(
            ConversationRepository conversationRepository,
            ConversationRunRepository conversationRunRepository,
            AgentChatService agentChatService,
            TitleGenerationService titleGenerationService
    ) {
        this.conversationRepository = conversationRepository;
        this.conversationRunRepository = conversationRunRepository;
        this.agentChatService = agentChatService;
        this.titleGenerationService = titleGenerationService;
    }

    @Override
    @Transactional
    public ConversationResponse createConversation(Long systemId, String title) {
        ConversationEntity entity = new ConversationEntity();
        entity.setConversationId(UUID.randomUUID().toString());
        entity.setSystemId(systemId);
        entity.setTitle(title != null && !title.isBlank() ? title : "新会话");
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
    public Page<ConversationResponse> listConversations(Long systemId, Pageable pageable) {
        return conversationRepository.findBySystemId(systemId, pageable)
                .map(this::toResponse);
    }

    @Override
    public ConversationResponse getConversation(String conversationId) {
        return conversationRepository.findByConversationId(conversationId)
                .map(this::toResponse)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONVERSATION_NOT_FOUND, "会话不存在: " + conversationId));
    }

    @Override
    public List<MessageResponse> getMessages(String conversationId) {
        // Rebuild messages from conversation_runs (request_text + final_answer)
        List<ConversationRunEntity> runs = conversationRunRepository
                .findByConversationIdAndStatusOrderByStartedAtAsc(conversationId, "success");

        List<MessageResponse> messages = new java.util.ArrayList<>();
        for (ConversationRunEntity run : runs) {
            if (run.getRequestText() != null && !run.getRequestText().isBlank()) {
                messages.add(new MessageResponse(
                        run.getId(),
                        "USER",
                        run.getRequestText(),
                        null,
                        null,
                        run.getStartedAt()
                ));
            }
            if (run.getFinalAnswer() != null && !run.getFinalAnswer().isBlank()) {
                LocalDateTime answeredAt = run.getFinishedAt() != null ? run.getFinishedAt() : run.getStartedAt();
                messages.add(new MessageResponse(
                        run.getId() + 1,
                        "ASSISTANT",
                        run.getFinalAnswer(),
                        null,
                        null,
                        answeredAt
                ));
            }
        }
        return messages;
    }

    @Override
    @Transactional
    public void deleteConversation(String conversationId) {
        // Delete conversation runs
        conversationRunRepository.findByConversationIdOrderByStartedAtDesc(conversationId)
                .forEach(conversationRunRepository::delete);

        // Delete conversation
        conversationRepository.findByConversationId(conversationId)
                .ifPresent(conversationRepository::delete);
    }

    @Override
    public String generateTitle(String conversationId) {
        // Get the first user message from the conversation
        List<MessageResponse> messages = getMessages(conversationId);
        
        MessageResponse firstUserMessage = messages.stream()
                .filter(m -> "USER".equalsIgnoreCase(m.role()))
                .findFirst()
                .orElse(null);
        
        if (firstUserMessage == null || firstUserMessage.content() == null || firstUserMessage.content().isBlank()) {
            return "新会话";
        }
        
        // Generate title using TitleGenerationService with fallback strategy
        String title = titleGenerationService.generateTitle(conversationId, firstUserMessage.content());
        
        // Update conversation title with retry logic
        executeWithRetry(conversationId, entity -> {
            entity.setTitle(title);
            entity.setUpdatedAt(LocalDateTime.now());
        });
        
        return title;
    }
    
    @Override
    public void updateTitle(String conversationId, String title) {
        executeWithRetry(conversationId, entity -> {
            entity.setTitle(title);
            entity.setUpdatedAt(LocalDateTime.now());
        });
    }

    /**
     * Execute an update operation with optimistic locking retry logic.
     * Retries up to MAX_RETRY_ATTEMPTS times with RETRY_DELAY_MS delay between attempts.
     * 
     * @param conversationId the conversation ID to update
     * @param updateAction the update action to perform on the conversation entity
     * @throws BusinessException with CONCURRENT_UPDATE_CONFLICT if all retries fail
     */
    private void executeWithRetry(String conversationId, Consumer<ConversationEntity> updateAction) {
        for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                ConversationEntity entity = conversationRepository.findByConversationId(conversationId)
                        .orElseThrow(() -> new BusinessException(
                                ErrorCode.CONVERSATION_NOT_FOUND,
                                "会话不存在: " + conversationId
                        ));
                
                // Apply the update action
                updateAction.accept(entity);
                
                // Save and return on success
                conversationRepository.save(entity);
                return;
                
            } catch (OptimisticLockException e) {
                log.warn("Optimistic lock conflict on conversation {} (attempt {}/{})",
                        conversationId, attempt, MAX_RETRY_ATTEMPTS);
                
                if (attempt >= MAX_RETRY_ATTEMPTS) {
                    throw new BusinessException(
                            ErrorCode.CONCURRENT_UPDATE_CONFLICT,
                            "会话正在被其他请求修改，请稍后重试"
                    );
                }
                
                // Sleep before retry
                try {
                    Thread.sleep(RETRY_DELAY_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new BusinessException(
                            ErrorCode.CONCURRENT_UPDATE_CONFLICT,
                            "重试被中断"
                    );
                }
            }
        }
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
