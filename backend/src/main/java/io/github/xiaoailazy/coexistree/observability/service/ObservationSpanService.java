package io.github.xiaoailazy.coexistree.observability.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.xiaoailazy.coexistree.observability.entity.ObservationSpanEntity;
import io.github.xiaoailazy.coexistree.observability.repository.ObservationSpanRepository;
import io.github.xiaoailazy.coexistree.shared.util.SnowflakeIdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class ObservationSpanService {

    private static final Logger log = LoggerFactory.getLogger(ObservationSpanService.class);

    private final ObservationSpanRepository spanRepository;
    private final SnowflakeIdGenerator snowflakeIdGenerator;
    private final ObjectMapper objectMapper;

    public ObservationSpanService(ObservationSpanRepository spanRepository,
                                  SnowflakeIdGenerator snowflakeIdGenerator,
                                  ObjectMapper objectMapper) {
        this.spanRepository = spanRepository;
        this.snowflakeIdGenerator = snowflakeIdGenerator;
        this.objectMapper = objectMapper;
    }

    // =========================================================================
    // Span creation
    // =========================================================================

    @Transactional
    public String startRunSpan(String runId, String traceId, String conversationId,
                               String agentName, String correlationId, LocalDateTime startedAt) {
        return startSpan(runId, traceId, conversationId, "run", "run:" + agentName,
                agentName, null, null, null, correlationId, startedAt);
    }

    @Transactional
    public String startAgentSpan(String runId, String traceId, String conversationId,
                                 String agentName, String parentSpanId, int depth,
                                 String correlationId, LocalDateTime startedAt) {
        return startSpan(runId, traceId, conversationId, "agent", "agent:" + agentName,
                agentName, null, null, parentSpanId, correlationId, startedAt);
    }

    @Transactional
    public String startModelSpan(String runId, String traceId, String conversationId,
                                 String agentName, String modelName, String parentSpanId,
                                 String correlationId, LocalDateTime startedAt) {
        return startSpan(runId, traceId, conversationId, "model", "model:" + modelName,
                agentName, null, modelName, parentSpanId, correlationId, startedAt);
    }

    @Transactional
    public String startToolSpan(String runId, String traceId, String conversationId,
                                String agentName, String toolName, String parentSpanId,
                                String correlationId, LocalDateTime startedAt) {
        return startSpan(runId, traceId, conversationId, "tool", "tool:" + toolName,
                agentName, toolName, null, parentSpanId, correlationId, startedAt);
    }

    private String startSpan(String runId, String traceId, String conversationId,
                             String spanType, String spanName, String agentName,
                             String toolName, String modelName, String parentSpanId,
                             String correlationId, LocalDateTime startedAt) {
        String spanId = "span-" + java.util.UUID.randomUUID().toString().replace("-", "");

        ObservationSpanEntity entity = new ObservationSpanEntity();
        entity.setId(snowflakeIdGenerator.nextId());
        entity.setTraceId(traceId);
        entity.setSpanId(spanId);
        entity.setParentSpanId(parentSpanId);
        entity.setRunId(runId);
        entity.setConversationId(conversationId);
        entity.setSpanType(spanType);
        entity.setSpanName(spanName);
        entity.setAgentName(agentName);
        entity.setToolName(toolName);
        entity.setModelName(modelName);
        entity.setStatus("running");
        entity.setStartedAt(startedAt);
        entity.setDepth(depthForType(spanType, parentSpanId));
        entity.setCorrelationId(correlationId);
        entity.setCreatedAt(LocalDateTime.now());

        spanRepository.save(entity);

        if ("run".equals(spanType)) {
            log.info("[span] run started, spanId={}, runId={}", spanId, runId);
        } else {
            log.debug("[span] started {} spanId={}, runId={}, parent={}",
                    spanType, spanId, runId, parentSpanId);
        }

        return spanId;
    }

    private int depthForType(String spanType, String parentSpanId) {
        if ("run".equals(spanType)) return 0;
        if (parentSpanId == null) return 1;
        return 2; // default for non-root spans; callbacks will compute actual depth
    }

    // =========================================================================
    // Span completion
    // =========================================================================

    @Transactional
    public void finishSpanSuccess(String spanId, Map<String, Object> outputPayload,
                                  Map<String, Object> attributes, long durationMs) {
        finishSpan(spanId, "success", null, outputPayload, attributes);
        setDuration(spanId, durationMs);
        log.info("[span] success, spanId={}, durationMs={}, attrs={}, output={}",
                spanId, durationMs, attributes != null ? attributes.keySet() : "null",
                outputPayload != null ? outputPayload.keySet() : "null");
    }

    @Transactional
    public void setInputPayload(String spanId, String json) {
        ObservationSpanEntity entity = spanRepository.findBySpanId(spanId)
                .orElseThrow(() -> new IllegalStateException("Span not found: " + spanId));
        entity.setInputPayload(json);
        spanRepository.save(entity);
        log.debug("[span] setInputPayload, spanId={}, payloadLen={}", spanId, json != null ? json.length() : 0);
    }

    @Transactional
    public void finishSpanFailed(String spanId, Map<String, Object> errorPayload, long durationMs) {
        finishSpan(spanId, "failed", errorPayload, null, null);
        setDuration(spanId, durationMs);
        log.info("[span] failed, spanId={}, durationMs={}, error={}",
                spanId, durationMs, errorPayload != null ? errorPayload : "null");
    }

    @Transactional
    public void finishSpanOrphaned(String spanId, String reason, long durationMs) {
        try {
            Map<String, Object> attrs = Map.of("orphan_reason", reason);
            finishSpan(spanId, "orphaned", null, null, attrs);
            setDuration(spanId, durationMs);
        } catch (Exception e) {
            log.error("[span] failed to orphan spanId={}", spanId, e);
        }
    }

    private void finishSpan(String spanId, String status, Map<String, Object> errorPayload,
                            Map<String, Object> outputPayload, Map<String, Object> attributes) {
        ObservationSpanEntity entity = spanRepository.findBySpanId(spanId)
                .orElseThrow(() -> new IllegalStateException("Span not found: " + spanId));

        entity.setStatus(status);
        entity.setFinishedAt(LocalDateTime.now());
        try {
            if (errorPayload != null) entity.setErrorPayload(objectMapper.writeValueAsString(errorPayload));
            if (outputPayload != null) entity.setOutputPayload(objectMapper.writeValueAsString(outputPayload));
            if (attributes != null) entity.setAttributes(objectMapper.writeValueAsString(attributes));
        } catch (Exception e) {
            log.error("[span] JSON serialization failed for spanId={}", spanId, e);
        }

        spanRepository.save(entity);

        log.debug("[span] {} -> {}", spanId, status);
    }

    private void setDuration(String spanId, long durationMs) {
        ObservationSpanEntity entity = spanRepository.findBySpanId(spanId)
                .orElseThrow(() -> new IllegalStateException("Span not found: " + spanId));
        entity.setDurationMs(durationMs);
        spanRepository.save(entity);
    }

    // =========================================================================
    // Finalizer
    // =========================================================================

    /**
     * Mark all still-running spans for a given run as orphaned or aborted.
     */
    @Transactional
    public int finalizeRunningSpansForRun(String runId, String reason) {
        List<ObservationSpanEntity> runningSpans =
                spanRepository.findByRunIdAndStatus(runId, "running");

        if (runningSpans.isEmpty()) return 0;

        String targetStatus = reason.contains("success") ? "orphaned" : "aborted";
        LocalDateTime now = LocalDateTime.now();

        for (ObservationSpanEntity span : runningSpans) {
            span.setStatus(targetStatus);
            span.setFinishedAt(now);
            span.setDurationMs(java.time.Duration.between(span.getStartedAt(), now).toMillis());

            try {
                String existingAttrs = span.getAttributes();
                Map<String, Object> attrs;
                if (existingAttrs != null && !existingAttrs.isEmpty()) {
                    attrs = objectMapper.readValue(existingAttrs, new TypeReference<>() {});
                } else {
                    attrs = new java.util.HashMap<>();
                }
                attrs.put("close_reason", reason);
                span.setAttributes(objectMapper.writeValueAsString(attrs));
            } catch (Exception e) {
                log.error("[finalizer] JSON error for spanId={}", span.getSpanId(), e);
            }

            spanRepository.save(span);
        }

        log.info("[finalizer] {} spans for runId={} -> {}", runningSpans.size(), runId, targetStatus);
        return runningSpans.size();
    }

}
