package io.github.xiaoailazy.coexistree.observability.service;

import io.github.xiaoailazy.coexistree.observability.context.RunContext;
import io.github.xiaoailazy.coexistree.observability.context.SpanContextRegistry;
import io.github.xiaoailazy.coexistree.observability.entity.ConversationRunEntity;
import io.github.xiaoailazy.coexistree.observability.repository.ConversationRunRepository;
import io.github.xiaoailazy.coexistree.observability.service.ObservationSpanService;
import io.github.xiaoailazy.coexistree.shared.util.SnowflakeIdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class ConversationRunService {

    private static final Logger log = LoggerFactory.getLogger(ConversationRunService.class);

    private final ConversationRunRepository runRepository;
    private final SnowflakeIdGenerator snowflakeIdGenerator;
    private final SpanContextRegistry spanContextRegistry;
    private final ObservationSpanService spanService;

    public ConversationRunService(ConversationRunRepository runRepository,
                                  SnowflakeIdGenerator snowflakeIdGenerator,
                                  SpanContextRegistry spanContextRegistry,
                                  ObservationSpanService spanService) {
        this.runRepository = runRepository;
        this.snowflakeIdGenerator = snowflakeIdGenerator;
        this.spanContextRegistry = spanContextRegistry;
        this.spanService = spanService;
    }

    /**
     * Creates a new run and registers it in the SpanContextRegistry.
     * MVP: traceId = runId (design doc reserves the field for future evolution).
     */
    @Transactional
    public RunContext createRun(String conversationId,
                                Long triggerMessageId,
                                Long userId,
                                Long systemId,
                                String rootAgentName,
                                String requestText,
                                String correlationId) {
        String runId = generateRunId();
        LocalDateTime now = LocalDateTime.now();

        ConversationRunEntity entity = new ConversationRunEntity();
        entity.setId(snowflakeIdGenerator.nextId());
        entity.setRunId(runId);
        entity.setTraceId(runId); // MVP: trace_id = run_id
        entity.setConversationId(conversationId);
        entity.setTriggerMessageId(triggerMessageId);
        entity.setUserId(userId);
        entity.setSystemId(systemId);
        entity.setRootAgentName(rootAgentName);
        entity.setRequestText(requestText);
        entity.setStatus("running");
        entity.setStartedAt(now);
        entity.setCorrelationId(correlationId);
        entity.setCreatedAt(now);

        runRepository.save(entity);

        RunContext ctx = new RunContext(
                runId, runId, conversationId, triggerMessageId,
                userId, systemId, rootAgentName, correlationId, now
        );
        spanContextRegistry.registerRun(ctx);

        log.info("[run] created runId={}, conv={}, user={}", runId, conversationId, userId);
        return ctx;
    }

    @Transactional
    public void markRunSuccess(String runId, String finalAnswer, long durationMs) {
        updateRunEnd(runId, "success", null, null, finalAnswer, durationMs);
        log.info("[run] success, runId={}, durationMs={}, answerLen={}",
                runId, durationMs, finalAnswer != null ? finalAnswer.length() : 0);
    }

    @Transactional
    public void markRunFailed(String runId, String errorCode, String errorMessage, long durationMs) {
        log.warn("[run] failed, runId={}, errorCode={}, errorMsg={}, durationMs={}",
                runId, errorCode, errorMessage, durationMs);
        updateRunEnd(runId, "failed", errorCode, errorMessage, null, durationMs);
    }

    private void updateRunEnd(String runId, String status,
                              String errorCode, String errorMessage,
                              String finalAnswer, long durationMs) {
        ConversationRunEntity entity = runRepository.findByRunId(runId)
                .orElseThrow(() -> new IllegalStateException("Run not found: " + runId));

        entity.setStatus(status);
        entity.setFinishedAt(LocalDateTime.now());
        entity.setDurationMs(durationMs);
        if (errorCode != null) entity.setErrorCode(errorCode);
        if (errorMessage != null) entity.setErrorMessage(errorMessage);
        if (finalAnswer != null) entity.setFinalAnswer(finalAnswer);

        runRepository.save(entity);

        // Finalize any still-running spans for this run
        int finalized = spanService.finalizeRunningSpansForRun(runId, "run_" + status);

        // Clear the registry bucket for this run
        spanContextRegistry.clearRun(runId);

        log.info("[run] runId={} -> {}, durationMs={}, spansFinalized={}", runId, status, durationMs, finalized);
    }

    private static String generateRunId() {
        return "run-" + UUID.randomUUID().toString().replace("-", "");
    }
}
