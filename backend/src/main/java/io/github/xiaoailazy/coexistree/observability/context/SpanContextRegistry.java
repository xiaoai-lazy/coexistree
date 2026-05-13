package io.github.xiaoailazy.coexistree.observability.context;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Runtime index for active span/invocation mappings within runs.
 * Volatile — loss of this data does NOT affect persisted span facts.
 *
 * Key design:
 * - runId -> RunRuntimeBucket (not flat maps, to isolate runs)
 * - invocationId only unique within a single run
 * - bounded by TTL and capacity to prevent unbounded growth
 */
@Component
public class SpanContextRegistry {

    private static final Logger log = LoggerFactory.getLogger(SpanContextRegistry.class);

    public record SpanRuntimeContext(
            String spanId,
            String parentSpanId,
            String agentName,
            int depth,
            LocalDateTime registeredAt
    ) {}

    static class RunRuntimeBucket {
        RunContext runContext;
        final Map<String, String> invocationToSpanId = new ConcurrentHashMap<>();
        final Map<String, SpanRuntimeContext> spanRuntimes = new ConcurrentHashMap<>();
        final Map<String, String> branchToSpanId = new ConcurrentHashMap<>();
        final LocalDateTime createdAt = LocalDateTime.now();
        volatile LocalDateTime lastAccessAt = LocalDateTime.now();
    }

    private final ConcurrentHashMap<String, RunRuntimeBucket> buckets = new ConcurrentHashMap<>();

    // Configurable limits
    private int maxActiveRuns = 1000;
    private int maxInvocationsPerRun = 256;
    private int maxSpanRuntimesPerRun = 512;
    private Duration invocationTtl = Duration.ofMinutes(60);
    private Duration runBucketTtl = Duration.ofHours(2);

    public void setMaxActiveRuns(int maxActiveRuns) { this.maxActiveRuns = maxActiveRuns; }
    public void setMaxInvocationsPerRun(int maxInvocationsPerRun) { this.maxInvocationsPerRun = maxInvocationsPerRun; }
    public void setMaxSpanRuntimesPerRun(int maxSpanRuntimesPerRun) { this.maxSpanRuntimesPerRun = maxSpanRuntimesPerRun; }
    public void setInvocationTtl(Duration invocationTtl) { this.invocationTtl = invocationTtl; }
    public void setRunBucketTtl(Duration runBucketTtl) { this.runBucketTtl = runBucketTtl; }

    // --- Run registration ---

    public void registerRun(RunContext ctx) {
        evictIfOverCapacity();
        buckets.computeIfAbsent(ctx.runId(), k -> {
            var b = new RunRuntimeBucket();
            b.runContext = ctx;
            log.info("[registry] run registered, runId={}, conv={}, activeRuns={}",
                    ctx.runId(), ctx.conversationId(), buckets.size());
            return b;
        });
    }

    public RunContext getRun(String runId) {
        RunRuntimeBucket b = buckets.get(runId);
        if (b != null) {
            b.lastAccessAt = LocalDateTime.now();
            return b.runContext;
        }
        return null;
    }

    // --- Invocation <-> spanId mapping ---

    public void registerInvocationSpan(String runId, String invocationId, String spanId, String parentSpanId, int depth) {
        RunRuntimeBucket b = buckets.get(runId);
        if (b == null) {
            log.warn("[registry] registerInvocationSpan: bucket not found for runId={}", runId);
            return;
        }
        b.lastAccessAt = LocalDateTime.now();

        if (b.invocationToSpanId.size() >= maxInvocationsPerRun) {
            log.warn("[registry] capacity exceeded for runId={}, dropping invocation (maxInvocationsPerRun={})",
                    runId, maxInvocationsPerRun);
            return;
        }

        b.invocationToSpanId.put(invocationId, spanId);
        b.spanRuntimes.put(spanId, new SpanRuntimeContext(spanId, parentSpanId, null, depth, LocalDateTime.now()));
    }

    public String findSpanIdByInvocation(String runId, String invocationId) {
        RunRuntimeBucket b = buckets.get(runId);
        if (b != null) {
            b.lastAccessAt = LocalDateTime.now();
            return b.invocationToSpanId.get(invocationId);
        }
        return null;
    }

    public void removeInvocation(String runId, String invocationId) {
        RunRuntimeBucket b = buckets.get(runId);
        if (b != null) {
            b.invocationToSpanId.remove(invocationId);
        }
    }

    // --- Span runtime context ---

    public void registerSpanRuntime(String spanId, SpanRuntimeContext ctx) {
        // Look up the runId from spanId — we need a reverse map for this
        for (var entry : buckets.entrySet()) {
            RunRuntimeBucket b = entry.getValue();
            if (b.spanRuntimes.containsKey(spanId)) {
                b.spanRuntimes.put(spanId, ctx);
                return;
            }
        }
    }

    public SpanRuntimeContext findSpanRuntime(String spanId) {
        for (var b : buckets.values()) {
            SpanRuntimeContext ctx = b.spanRuntimes.get(spanId);
            if (ctx != null) return ctx;
        }
        return null;
    }

    public void removeSpanRuntime(String spanId) {
        for (var b : buckets.values()) {
            b.spanRuntimes.remove(spanId);
        }
    }

    // --- Cleanup ---

    public void clearRun(String runId) {
        RunRuntimeBucket removed = buckets.remove(runId);
        if (removed != null) {
            log.info("[registry] run cleared, runId={}, invocations={}, spans={}",
                    runId, removed.invocationToSpanId.size(), removed.spanRuntimes.size());
        } else {
            log.warn("[registry] run cleared but bucket not found, runId={}", runId);
        }
    }

    public void evictExpiredRuns(LocalDateTime now) {
        int beforeCount = buckets.size();
        boolean changed = buckets.entrySet().removeIf(entry -> {
            RunRuntimeBucket b = entry.getValue();
            LocalDateTime cutoff = now.minus(runBucketTtl);
            return b.lastAccessAt.isBefore(cutoff);
        });
        if (changed && buckets.size() < beforeCount) {
            int evicted = beforeCount - buckets.size();
            log.info("[registry] evicted {} expired runs, before={}, after={}",
                    evicted, beforeCount, buckets.size());
        }
    }

    public void evictIfOverCapacity() {
        if (buckets.size() <= maxActiveRuns) return;

        // Find oldest unused run
        String oldest = null;
        LocalDateTime oldestAccess = LocalDateTime.now();
        for (var entry : buckets.entrySet()) {
            if (entry.getValue().lastAccessAt.isBefore(oldestAccess)) {
                oldestAccess = entry.getValue().lastAccessAt;
                oldest = entry.getKey();
            }
        }
        if (oldest != null) {
            buckets.remove(oldest);
            log.warn("[registry] evicted oldest run to stay under capacity (maxActiveRuns={}), evicted={}",
                    maxActiveRuns, oldest);
        }
    }

    /**
     * Find parent span ID for a given invocation within a run.
     */
    public String findParentSpanId(String runId, String invocationId) {
        RunRuntimeBucket b = buckets.get(runId);
        if (b == null) return null;
        b.lastAccessAt = LocalDateTime.now();

        String spanId = b.invocationToSpanId.get(invocationId);
        if (spanId == null) return null;

        SpanRuntimeContext ctx = b.spanRuntimes.get(spanId);
        return ctx != null ? ctx.parentSpanId : null;
    }

    /**
     * Find the most recently registered active (unclosed) span in a run.
     * Used by beforeAgent to determine parent when entering a nested agent.
     */
    public String findLatestActiveSpanId(String runId) {
        RunRuntimeBucket b = buckets.get(runId);
        if (b == null) return null;
        b.lastAccessAt = LocalDateTime.now();

        String latest = null;
        LocalDateTime latestTime = LocalDateTime.MIN;
        for (var entry : b.spanRuntimes.entrySet()) {
            // A span is "active" if it hasn't been removed from invocationToSpanId
            // (i.e., its agent hasn't completed yet)
            SpanRuntimeContext ctx = entry.getValue();
            if (ctx.registeredAt().isAfter(latestTime)) {
                latestTime = ctx.registeredAt();
                latest = entry.getKey();
            }
        }
        return latest;
    }
}
