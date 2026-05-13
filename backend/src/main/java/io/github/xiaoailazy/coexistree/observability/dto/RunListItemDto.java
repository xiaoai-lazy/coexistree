package io.github.xiaoailazy.coexistree.observability.dto;

import java.time.LocalDateTime;

/**
 * Lightweight item for run list display.
 */
public record RunListItemDto(
        String runId,
        String status,
        String requestPreview,
        String finalAnswerPreview,
        Long durationMs,
        LocalDateTime startedAt,
        Integer spanCount
) {}
