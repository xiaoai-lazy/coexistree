package io.github.xiaoailazy.coexistree.shared.util;

import java.util.Optional;

/**
 * ThreadLocal context for LLM call tracking.
 * Carries scenario/documentId/systemId/userId through the call chain.
 * Must be set before llmClient.chat() and cleared afterwards.
 */
public final class LlmCallContext {

    private static final ThreadLocal<LlmCallInfo> CONTEXT = new ThreadLocal<>();

    public record LlmCallInfo(
        String scenario,
        Long documentId,
        Long systemId,
        Long userId
    ) {}

    /**
     * Set the current LLM call context.
     * @param scenario Business scenario name (e.g., "BASELINE_MERGE", "NODE_SUMMARY")
     * @param documentId Associated document ID, or null
     * @param systemId Associated system ID, or null
     * @param userId User who triggered the call, or null (e.g., async tasks)
     */
    public static void set(String scenario, Long documentId, Long systemId, Long userId) {
        CONTEXT.set(new LlmCallInfo(scenario, documentId, systemId, userId));
    }

    public static Optional<LlmCallInfo> get() {
        return Optional.ofNullable(CONTEXT.get());
    }

    public static void clear() {
        CONTEXT.remove();
    }

    private LlmCallContext() {}
}
