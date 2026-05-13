package io.github.xiaoailazy.coexistree.observability.repository;

import io.github.xiaoailazy.coexistree.observability.entity.ObservationSpanEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ObservationSpanRepository extends JpaRepository<ObservationSpanEntity, Long> {

    Optional<ObservationSpanEntity> findBySpanId(String spanId);

    List<ObservationSpanEntity> findByRunIdOrderByStartedAtAsc(String runId);

    @Query("SELECT s FROM ObservationSpanEntity s WHERE s.runId = :runId AND s.status = :status ORDER BY s.startedAt")
    List<ObservationSpanEntity> findByRunIdAndStatus(
            @Param("runId") String runId,
            @Param("status") String status);

    @Query("SELECT s FROM ObservationSpanEntity s WHERE s.status = :status AND s.startedAt < :before ORDER BY s.startedAt")
    List<ObservationSpanEntity> findByStatusAndStartedAtBefore(
            @Param("status") String status,
            @Param("before") LocalDateTime before);

    List<ObservationSpanEntity> findByConversationIdOrderByStartedAtAsc(String conversationId);

    @Query("SELECT DISTINCT s.runId FROM ObservationSpanEntity s WHERE s.conversationId = :conversationId")
    List<String> findDistinctRunIdsByConversationId(@Param("conversationId") String conversationId);
}
