package io.github.xiaoailazy.coexistree.observability.context;

import java.time.LocalDateTime;

/**
 * Immutable run-scoped context carrying stable boundary information.
 * Does NOT hold dynamic span topology data (that's SpanContextRegistry's job).
 */
public record RunContext(
        String runId,
        String traceId,
        String conversationId,
        Long triggerMessageId,
        Long userId,
        Long systemId,
        String rootAgentName,
        String correlationId,
        LocalDateTime startedAt
) {
}
