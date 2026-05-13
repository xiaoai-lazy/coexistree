package io.github.xiaoailazy.coexistree.observability.dto;

import java.time.LocalDateTime;

/**
 * A timeline card item for run-level event display.
 */
public record RunTimelineItemDto(
        String eventType,
        String spanId,
        String spanType,
        String spanName,
        String detail,
        LocalDateTime occurredAt,
        Long sequenceNo
) {}
