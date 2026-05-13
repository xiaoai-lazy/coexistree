package io.github.xiaoailazy.coexistree.chat.repository;

import io.github.xiaoailazy.coexistree.chat.entity.ConversationEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConversationRepository extends JpaRepository<ConversationEntity, Long> {
    Optional<ConversationEntity> findByConversationId(String conversationId);
    List<ConversationEntity> findAllByOrderByUpdatedAtDesc();
    
    /**
     * Find conversations by system ID with pagination support
     * @param systemId the system ID
     * @param pageable pagination parameters
     * @return page of conversations
     */
    Page<ConversationEntity> findBySystemId(Long systemId, Pageable pageable);
    
    /**
     * Find top 100 conversations ordered by last update time (descending)
     * Used for ADK session restoration on server startup
     * @return list of recent conversations
     */
    List<ConversationEntity> findTop100ByOrderByUpdatedAtDesc();
}
