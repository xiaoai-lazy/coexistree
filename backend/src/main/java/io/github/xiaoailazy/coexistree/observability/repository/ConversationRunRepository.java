package io.github.xiaoailazy.coexistree.observability.repository;

import io.github.xiaoailazy.coexistree.observability.entity.ConversationRunEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ConversationRunRepository extends JpaRepository<ConversationRunEntity, Long> {

    Optional<ConversationRunEntity> findByRunId(String runId);

    List<ConversationRunEntity> findByConversationIdOrderByStartedAtDesc(String conversationId);

    List<ConversationRunEntity> findByConversationIdAndStatusOrderByStartedAtAsc(String conversationId, String status);

    @Query("SELECT r FROM ConversationRunEntity r WHERE r.status = :status AND r.startedAt < :before ORDER BY r.startedAt")
    List<ConversationRunEntity> findByStatusAndStartedAtBefore(
            @Param("status") String status,
            @Param("before") LocalDateTime before);

    long countByStatus(String status);

    Optional<ConversationRunEntity> findTopByConversationIdAndStatusOrderByStartedAtDesc(
            String conversationId, String status);
}
