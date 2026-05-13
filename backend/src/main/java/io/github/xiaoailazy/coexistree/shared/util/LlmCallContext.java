package io.github.xiaoailazy.coexistree.shared.util;

import java.util.Optional;

/**
 * ThreadLocal context for LLM call tracking.
 * Carries scenario/processLogId/documentId/systemId/userId through the call chain.
 * Must be set before llmClient.chat() and cleared afterwards.
 */
public final class LlmCallContext {

    private static final ThreadLocal<LlmCallInfo> CONTEXT = new ThreadLocal<>();

    public record LlmCallInfo(
        String scenario,
        Long processLogId,
        Long documentId,
        Long systemId,
        Long userId,
        String correlationId
    ) {}

    /**
     * Set the current LLM call context with processLogId.
     */
    public static void set(String scenario, Long processLogId, Long documentId, Long systemId, Long userId) {
        CONTEXT.set(new LlmCallInfo(scenario, processLogId, documentId, systemId, userId, null));
    }

    /**
     * Set the current LLM call context with processLogId and correlation ID.
     */
    public static void set(String scenario, Long processLogId, Long documentId, Long systemId, Long userId, String correlationId) {
        CONTEXT.set(new LlmCallInfo(scenario, processLogId, documentId, systemId, userId, correlationId));
    }

    public static Optional<LlmCallInfo> get() {
        return Optional.ofNullable(CONTEXT.get());
    }

    public static void clear() {
        CONTEXT.remove();
    }

    private LlmCallContext() {}
}
