package io.github.xiaoailazy.coexistree.observability.repository;

import io.github.xiaoailazy.coexistree.observability.entity.SpanEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SpanEventRepository extends JpaRepository<SpanEventEntity, Long> {

    List<SpanEventEntity> findByRunIdOrderBySequenceNoAsc(String runId);

    @Query("SELECT e FROM SpanEventEntity e WHERE e.spanId = :spanId ORDER BY e.sequenceNo ASC")
    List<SpanEventEntity> findBySpanIdOrderBySequenceNoAsc(@Param("spanId") String spanId);

    @Query("SELECT COUNT(e) FROM SpanEventEntity e WHERE e.runId = :runId")
    int countByRunId(@Param("runId") String runId);

    List<SpanEventEntity> findByConversationIdOrderByOccurredAtAsc(String conversationId);

    @Query("SELECT COUNT(e) FROM SpanEventEntity e WHERE e.conversationId = :conversationId")
    int countByConversationId(@Param("conversationId") String conversationId);
}
