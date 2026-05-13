package io.github.xiaoailazy.coexistree.observability.dto;

import java.time.LocalDateTime;

/**
 * Aggregated summary of a single conversation run.
 */
public record RunSummaryDto(
        String runId,
        String traceId,
        String conversationId,
        String status,
        String rootAgentName,
        String requestText,
        String finalAnswer,
        Long durationMs,
        Integer totalSpanCount,
        Integer modelCallCount,
        Integer toolCallCount,
        Integer agentSpanCount,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        String correlationId
) {}
