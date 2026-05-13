package io.github.xiaoailazy.coexistree.chat.repository;

import io.github.xiaoailazy.coexistree.chat.entity.MessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface MessageRepository extends JpaRepository<MessageEntity, Long> {
    /**
     * Find all messages for a conversation ordered by creation time (ascending)
     * @param conversationId the conversation ID
     * @return list of messages
     */
    List<MessageEntity> findByConversationIdOrderByCreatedAt(String conversationId);
    
    /**
     * Find top N messages for a conversation ordered by creation time (descending)
     * Used for building ADK context with recent messages
     * @param conversationId the conversation ID
     * @param limit maximum number of messages to return
     * @return list of recent messages
     */
    @Query("SELECT m FROM MessageEntity m WHERE m.conversationId = :conversationId " +
           "ORDER BY m.createdAt DESC LIMIT :limit")
    List<MessageEntity> findTopNByConversationIdOrderByCreatedAtDesc(
        @Param("conversationId") String conversationId,
        @Param("limit") int limit
    );
    
    /**
     * Count messages in a conversation
     * @param conversationId the conversation ID
     * @return message count
     */
    int countByConversationId(String conversationId);
    
    /**
     * Delete all messages for a conversation
     * @param conversationId the conversation ID
     */
    @Modifying
    @Transactional
    void deleteByConversationId(String conversationId);

    /**
     * Find the ID of the latest user message in a conversation.
     * @param conversationId the conversation ID
     * @return the message ID, or null if no user message exists
     */
    @Query("SELECT m.id FROM MessageEntity m WHERE m.conversationId = :conversationId " +
           "AND m.role = 'user' ORDER BY m.createdAt DESC LIMIT 1")
    Long findLatestUserMessageId(@Param("conversationId") String conversationId);
}
