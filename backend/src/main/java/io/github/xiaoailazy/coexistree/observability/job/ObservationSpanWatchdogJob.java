package io.github.xiaoailazy.coexistree.observability.job;

import io.github.xiaoailazy.coexistree.observability.context.SpanContextRegistry;
import io.github.xiaoailazy.coexistree.observability.entity.ConversationRunEntity;
import io.github.xiaoailazy.coexistree.observability.entity.ObservationSpanEntity;
import io.github.xiaoailazy.coexistree.observability.repository.ConversationRunRepository;
import io.github.xiaoailazy.coexistree.observability.repository.ObservationSpanRepository;
import io.github.xiaoailazy.coexistree.observability.service.ObservationSpanService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Safety-net job that finds spans stuck in {@code status=running} past their
 * expected lifetime and forcibly closes them as {@code orphaned}.
 *
 * Covers scenarios where the synchronous finalizer in
 * {@code ConversationRunService.updateRunEnd} could not execute:
 * process crash, OOM, callback failure, etc.
 */
@Component
public class ObservationSpanWatchdogJob {

    private static final Logger log = LoggerFactory.getLogger(ObservationSpanWatchdogJob.class);

    private static final String STATUS_RUNNING = "running";

    private final ObservationSpanRepository spanRepository;
    private final ConversationRunRepository runRepository;
    private final ObservationSpanService spanService;
    private final SpanContextRegistry contextRegistry;

    // Configurable timeouts (milliseconds)
    @Value("${observability.watchdog.model-timeout-ms:300000}")
    private long modelTimeoutMs;

    @Value("${observability.watchdog.tool-timeout-ms:120000}")
    private long toolTimeoutMs;

    @Value("${observability.watchdog.agent-timeout-ms:600000}")
    private long agentTimeoutMs;

    @Value("${observability.watchdog.run-timeout-ms:1800000}")
    private long runTimeoutMs;

    public ObservationSpanWatchdogJob(ObservationSpanRepository spanRepository,
                                      ConversationRunRepository runRepository,
                                      ObservationSpanService spanService,
                                      SpanContextRegistry contextRegistry) {
        this.spanRepository = spanRepository;
        this.runRepository = runRepository;
        this.spanService = spanService;
        this.contextRegistry = contextRegistry;
    }

    /**
     * Run every 60 seconds. Finds running spans past their timeout cutoff and
     * marks them orphaned.
     */
    @Scheduled(fixedDelay = 60_000, initialDelay = 120_000)
    public void execute() {
        LocalDateTime now = LocalDateTime.now();
        int orphanedCount = 0;

        // Track runs that had spans orphaned this round, for post-cleanup
        Map<String, Boolean> runsWithNewOrphans = new HashMap<>();

        // Process each span type with its own timeout
        orphanedCount += checkType("model", modelTimeoutMs, now, runsWithNewOrphans);
        orphanedCount += checkType("tool", toolTimeoutMs, now, runsWithNewOrphans);
        orphanedCount += checkType("agent", agentTimeoutMs, now, runsWithNewOrphans);
        orphanedCount += checkType("run", runTimeoutMs, now, runsWithNewOrphans);

        // After orphaning spans, clear context for runs whose all spans are now terminal
        for (String runId : runsWithNewOrphans.keySet()) {
            if (areAllSpansTerminal(runId)) {
                contextRegistry.clearRun(runId);
                log.debug("[watchdog] cleared SpanContextRegistry for runId={}", runId);
            }
        }

        if (orphanedCount > 0) {
            log.warn("[watchdog] cycle complete: {} span(s) orphaned", orphanedCount);
        } else {
            log.debug("[watchdog] cycle complete: no orphaned spans found");
        }
    }

    private int checkType(String spanType, long timeoutMs, LocalDateTime now,
                          Map<String, Boolean> runsWithNewOrphans) {
        LocalDateTime cutoff = now.minus(Duration.ofMillis(timeoutMs));
        List<ObservationSpanEntity> timedOutSpans =
                spanRepository.findByStatusAndStartedAtBefore(STATUS_RUNNING, cutoff);

        int count = 0;
        for (ObservationSpanEntity span : timedOutSpans) {
            // Only process spans of the target type
            if (!spanType.equals(span.getSpanType())) {
                continue;
            }

            String runId = span.getRunId();
            String spanId = span.getSpanId();
            long elapsedMs = Duration.between(span.getStartedAt(), now).toMillis();

            Optional<ConversationRunEntity> runOpt = runRepository.findByRunId(runId);

            if (runOpt.isPresent()) {
                ConversationRunEntity run = runOpt.get();
                String runStatus = run.getStatus();

                if (isTerminalStatus(runStatus)) {
                    // Run is done but span is still running -> orphan it
                    log.warn("[watchdog] orphaning span: runId={}, spanId={}, spanType={}, "
                            + "startedAt={}, elapsedMs={}, runStatus={}",
                            runId, spanId, spanType, span.getStartedAt(), elapsedMs, runStatus);

                    try {
                        spanService.finishSpanOrphaned(spanId, "watchdog_timeout", elapsedMs);
                        count++;
                        runsWithNewOrphans.put(runId, Boolean.TRUE);
                    } catch (Exception e) {
                        log.error("[watchdog] failed to orphan spanId={}", spanId, e);
                    }
                } else {
                    // Run is also still running -> skip, let run watchdog handle it
                    log.debug("[watchdog] skipping spanId={}, runId={} (run also running, elapsedMs={})",
                            spanId, runId, elapsedMs);
                }
            } else {
                // Run entity not found -> orphan the span (data inconsistency or
                // orphaned run)
                log.warn("[watchdog] orphaning span (run entity missing): "
                        + "runId={}, spanId={}, spanType={}, startedAt={}, elapsedMs={}",
                        runId, spanId, spanType, span.getStartedAt(), elapsedMs);

                try {
                    spanService.finishSpanOrphaned(spanId, "watchdog_timeout_run_missing", elapsedMs);
                    count++;
                    runsWithNewOrphans.put(runId, Boolean.TRUE);
                } catch (Exception e) {
                    log.error("[watchdog] failed to orphan spanId={}", spanId, e);
                }
            }
        }
        return count;
    }

    private boolean isTerminalStatus(String status) {
        return "success".equals(status) || "failed".equals(status)
                || "cancelled".equals(status) || "aborted".equals(status);
    }

    private boolean areAllSpansTerminal(String runId) {
        List<ObservationSpanEntity> runningSpans =
                spanRepository.findByRunIdAndStatus(runId, STATUS_RUNNING);
        return runningSpans.isEmpty();
    }
}
