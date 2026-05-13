package io.github.xiaoailazy.coexistree.observability.job;

import io.github.xiaoailazy.coexistree.observability.entity.ConversationRunEntity;
import io.github.xiaoailazy.coexistree.observability.repository.ConversationRunRepository;
import io.github.xiaoailazy.coexistree.observability.service.ConversationRunService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Periodic watchdog that finds runs stuck in "running" status and marks them as failed.
 * Covers cases where the HTTP connection dropped, the agent never completed, or the
 * process crashed before reaching markRunSuccess/markRunFailed.
 */
@Component
public class ConversationRunWatchdogJob {

    private static final Logger log = LoggerFactory.getLogger(ConversationRunWatchdogJob.class);

    private final ConversationRunService conversationRunService;
    private final ConversationRunRepository conversationRunRepository;

    @Value("${observability.watchdog.run-timeout:1800000}")
    private long runTimeoutMs;

    public ConversationRunWatchdogJob(ConversationRunService conversationRunService,
                                      ConversationRunRepository conversationRunRepository) {
        this.conversationRunService = conversationRunService;
        this.conversationRunRepository = conversationRunRepository;
    }

    @Scheduled(fixedDelay = 120000)
    public void execute() {
        log.debug("[watchdog] checking for timed-out runs (timeout={}ms)", runTimeoutMs);

        LocalDateTime cutoffTime = LocalDateTime.now().minusNanos(Duration.ofMillis(runTimeoutMs).toNanos());
        List<ConversationRunEntity> timedOutRuns = conversationRunRepository.findByStatusAndStartedAtBefore("running", cutoffTime);

        if (timedOutRuns.isEmpty()) {
            log.debug("[watchdog] no timed-out runs found");
            return;
        }

        log.info("[watchdog] found {} timed-out run(s), marking as failed", timedOutRuns.size());

        for (ConversationRunEntity run : timedOutRuns) {
            try {
                long elapsedMs = Duration.between(run.getStartedAt(), LocalDateTime.now()).toMillis();

                conversationRunService.markRunFailed(
                        run.getRunId(),
                        "watchdog_timeout",
                        "Run exceeded maximum duration: " + runTimeoutMs + "ms",
                        elapsedMs
                );

                log.warn("[watchdog] timed out runId={}, conversationId={}, elapsed={}ms",
                        run.getRunId(), run.getConversationId(), elapsedMs);
            } catch (Exception e) {
                log.error("[watchdog] failed to process runId={}", run.getRunId(), e);
            }
        }

        log.debug("[watchdog] check complete");
    }
}
