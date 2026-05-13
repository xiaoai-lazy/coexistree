package io.github.xiaoailazy.coexistree.observability.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * A node in the span tree, with children already assembled.
 */
public record SpanTreeNodeDto(
        String spanId,
        String parentSpanId,
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
        Long durationMs,
        Integer depth,
        List<SpanTreeNodeDto> children,
        LocalDateTime startedAt,
        LocalDateTime finishedAt
) {}
