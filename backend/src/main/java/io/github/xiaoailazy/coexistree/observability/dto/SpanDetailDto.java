package io.github.xiaoailazy.coexistree.observability.dto;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Detailed view of a single span.
 */
public record SpanDetailDto(
        String spanId,
        String parentSpanId,
        String runId,
        String spanType,
        String spanName,
        String agentName,
        String toolName,
        String modelName,
        String modelVersion,
        String status,
        Map<String, Object> inputPayload,
        Map<String, Object> outputPayload,
        Map<String, Object> attributes,
        Map<String, Object> errorPayload,
        Long durationMs,
        Integer depth,
        LocalDateTime startedAt,
        LocalDateTime finishedAt
) {}
