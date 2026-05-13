package io.github.xiaoailazy.coexistree.observability.service;

import io.github.xiaoailazy.coexistree.observability.config.SpanEventCollectionConfig;
import io.github.xiaoailazy.coexistree.observability.entity.SpanEventEntity;
import io.github.xiaoailazy.coexistree.observability.repository.SpanEventRepository;
import io.github.xiaoailazy.coexistree.shared.util.SnowflakeIdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Async buffer for span_events. Batches writes to avoid per-event DB transactions.
 *
 * Sequence numbers are allocated at buffer-entry time (run-level monotonic).
 * occurred_at is set at buffer-entry time (not flush time).
 *
 * Flush triggers:
 * - run end (explicit flushAndClose)
 * - error occurrence (immediate flush)
 * - buffer size exceeds threshold
 * - periodic time-based flush (via @Scheduled)
 */
@Service
public class SpanEventBuffer {

    private static final Logger log = LoggerFactory.getLogger(SpanEventBuffer.class);

    /** Run-level monotonic sequence counter */
    private final ConcurrentHashMap<String, AtomicLong> runSequences = new ConcurrentHashMap<>();

    /** Buffered events keyed by runId */
    private final ConcurrentHashMap<String, List<BufferedEvent>> buffers = new ConcurrentHashMap<>();

    /** Track failed runs for enhanced collection */
    private final ConcurrentHashMap<String, Boolean> failedRuns = new ConcurrentHashMap<>();

    private final SpanEventRepository eventRepository;
    private final SnowflakeIdGenerator snowflakeIdGenerator;
    private final SpanEventCollectionConfig config;

    public SpanEventBuffer(SpanEventRepository eventRepository,
                           SnowflakeIdGenerator snowflakeIdGenerator,
                           SpanEventCollectionConfig config) {
        this.eventRepository = eventRepository;
        this.snowflakeIdGenerator = snowflakeIdGenerator;
        this.config = config;
    }

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Enqueue a span event. Mode controls what gets buffered.
     * For keypoints mode: only milestone events pass through.
     * For batched mode: delta events are accumulated and merged.
     * For full mode: everything passes through directly.
     */
    public void enqueue(String spanId, String runId, String conversationId,
                        String eventType, String agentName,
                        String correlationId, Map<String, Object> payload) {

        // Filter by mode
        if (!shouldRecord(eventType)) return;

        // For batched mode, merge delta events
        if ("batched".equals(config.getMode()) && isDeltaEvent(eventType)) {
            accumulateDelta(runId, spanId, conversationId, eventType, agentName, correlationId, payload);
            return;
        }

        // Allocate sequence number at entry time
        long seqNo = nextSequence(runId);
        LocalDateTime occurredAt = LocalDateTime.now();

        BufferedEvent evt = new BufferedEvent(
                spanId, runId, conversationId, eventType,
                agentName, correlationId, payload, seqNo, occurredAt);

        buffers.computeIfAbsent(runId, k -> new ArrayList<>()).add(evt);
        log.debug("[span_event] enqueued, runId={}, type={}, seqNo={}, buffer={}",
                runId, eventType, seqNo, buffers.get(runId).size());

        // Flush if buffer is getting large
        List<BufferedEvent> buf = buffers.get(runId);
        if (buf.size() >= 50) {
            log.debug("[span_event] buffer threshold reached for runId={}, flushing", runId);
            flush(runId);
        }
    }

    /**
     * Flush and close the buffer for a run that has ended.
     */
    public void flushAndClose(String runId) {
        int pendingSize = buffers.containsKey(runId) ? buffers.get(runId).size() : 0;
        log.info("[span_event] flushAndClose, runId={}, pendingEvents={}", runId, pendingSize);
        flush(runId);
        buffers.remove(runId);
        runSequences.remove(runId);
    }

    /**
     * Mark a run as failed — triggers immediate full flush for debugging.
     */
    public void markRunFailed(String runId) {
        failedRuns.put(runId, true);
        log.warn("[span_event] run marked as failed, runId={}, persistFull={}",
                runId, config.isPersistFailedRunsFull());
        if (config.isPersistFailedRunsFull()) {
            flush(runId);
        }
    }

    /**
     * Flush buffered events for a run to the database.
     */
    public void flush(String runId) {
        List<BufferedEvent> buf = buffers.remove(runId);
        if (buf == null || buf.isEmpty()) return;

        try {
            List<SpanEventEntity> entities = new ArrayList<>(buf.size());
            for (BufferedEvent evt : buf) {
                SpanEventEntity entity = new SpanEventEntity();
                entity.setId(snowflakeIdGenerator.nextId());
                entity.setRunId(evt.runId());
                entity.setSpanId(evt.spanId());
                entity.setConversationId(evt.conversationId());
                entity.setEventType(evt.eventType());
                entity.setEventName(evt.eventType());
                entity.setSequenceNo(evt.seqNo());
                entity.setOccurredAt(evt.occurredAt());
                entity.setCorrelationId(evt.correlationId());
                entity.setCreatedAt(LocalDateTime.now());

                if (evt.payload() != null && !evt.payload().isEmpty()) {
                    entity.setPayload(serializeJson(evt.payload()));
                }

                entities.add(entity);
            }

            eventRepository.saveAll(entities);
            log.info("[span_event] flushed {} events for runId={}", entities.size(), runId);
        } catch (Exception e) {
            log.error("[span_event] flush failed for runId={}", runId, e);
        }
    }

    /**
     * Periodic flush for buffered events that exceed the time window.
     */
    @Scheduled(fixedDelayString = "${observability.span-events.batch-window-ms:3000}")
    public void periodicFlush() {
        int flushedCount = 0;
        for (String runId : List.copyOf(buffers.keySet())) {
            List<BufferedEvent> buf = buffers.get(runId);
            if (buf == null || buf.isEmpty()) continue;

            // Only flush if the oldest event exceeds the batch window
            long ageMs = java.time.Duration.between(buf.get(0).occurredAt(), LocalDateTime.now()).toMillis();
            if (ageMs >= config.getBatchWindowMs()) {
                flush(runId);
                flushedCount++;
            }
        }
        if (flushedCount > 0) {
            log.debug("[span_event] periodicFlush: flushed {} runs", flushedCount);
        }
    }

    // =========================================================================
    // Mode filtering
    // =========================================================================

    private boolean shouldRecord(String eventType) {
        String mode = config.getMode();
        if ("full".equals(mode)) return true;
        // keypoints and batched: only milestone events
        return KEYPOINT_EVENTS.contains(eventType);
    }

    private boolean isDeltaEvent(String eventType) {
        return "answer_delta_batch".equals(eventType) || "thinking_delta_batch".equals(eventType);
    }

    private static final java.util.Set<String> KEYPOINT_EVENTS = java.util.Set.of(
            "stream_started",
            "first_token",
            "thinking_started",
            "thinking_finished",
            "tool_decision",
            "stream_finished",
            "retry",
            "warning",
            "error",
            "answer_delta_batch",
            "thinking_delta_batch"
    );

    // =========================================================================
    // Delta accumulation
    // =========================================================================

    /**
     * Accumulate delta events into a single batched event.
     * Flushes when max characters or time window is exceeded.
     */
    private void accumulateDelta(String runId, String spanId, String conversationId,
                                 String eventType, String agentName,
                                 String correlationId, Map<String, Object> payload) {

        String key = runId + ":" + spanId + ":" + eventType;
        DeltaAccumulator accumulator = deltaAccumulators.computeIfAbsent(key, k -> new DeltaAccumulator(
                spanId, runId, conversationId, eventType, agentName, correlationId));

        Object content = payload.get("contentPreview");
        if (content != null) {
            accumulator.append(content.toString());
        }

        // Flush if batch is full
        if (accumulator.accumulatedChars() >= config.getBatchMaxChars()) {
            DeltaAccumulator flushed = deltaAccumulators.remove(key);
            if (flushed != null) {
                long seqNo = nextSequence(runId);
                LocalDateTime occurredAt = LocalDateTime.now();
                Map<String, Object> batchPayload = Map.of(
                        "contentBatch", flushed.content().toString()
                );
                BufferedEvent evt = new BufferedEvent(
                        flushed.spanId(), flushed.runId(), flushed.conversationId(),
                        flushed.eventType(), flushed.agentName(), flushed.correlationId(),
                        batchPayload, seqNo, occurredAt);
                buffers.computeIfAbsent(runId, k -> new ArrayList<>()).add(evt);
            }
        }
    }

    private final ConcurrentHashMap<String, DeltaAccumulator> deltaAccumulators = new ConcurrentHashMap<>();

    record DeltaAccumulator(String spanId, String runId, String conversationId,
                            String eventType, String agentName, String correlationId,
                            StringBuilder content, AtomicLong charCount) {
        DeltaAccumulator(String spanId, String runId, String conversationId,
                         String eventType, String agentName, String correlationId) {
            this(spanId, runId, conversationId, eventType, agentName, correlationId,
                 new StringBuilder(), new AtomicLong(0));
        }

        void append(String text) {
            content.append(text);
            charCount.addAndGet(text.length());
        }

        long accumulatedChars() { return charCount.get(); }
    }

    // =========================================================================
    // Sequence allocation
    // =========================================================================

    private long nextSequence(String runId) {
        return runSequences.computeIfAbsent(runId, k -> new AtomicLong(0))
                .incrementAndGet();
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private String serializeJson(Map<String, Object> payload) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(payload);
        } catch (Exception e) {
            return payload.toString();
        }
    }

    record BufferedEvent(String spanId, String runId, String conversationId,
                         String eventType, String agentName, String correlationId,
                         Map<String, Object> payload, long seqNo, LocalDateTime occurredAt) {}
}
