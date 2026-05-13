package io.github.xiaoailazy.coexistree.observability.model;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Stateless resolver for LLM model names.
 *
 * <p>Reads the default model from Spring configuration ({@code app.adk.model}
 * or {@code llm.openai.model}). Currently returns the default for all callers,
 * but is designed to support per-agent / per-scenario resolution in the future.
 */
@Component
public class ModelDescriptorResolver {

    private final String defaultModel;

    public ModelDescriptorResolver(
            @Value("${app.adk.model:#{null}}") String adkModel,
            @Value("${llm.openai.model:gpt-4o-mini}") String fallbackModel) {
        // Prefer explicit ADK model override; fall back to LLM properties;
        // then to hardcoded default. Mirrors the logic in AgentConfig.adkLlm().
        this.defaultModel = (adkModel != null && !adkModel.isBlank())
                ? adkModel
                : fallbackModel;
    }

    /**
     * Returns the configured default model name.
     */
    public String getDefaultModelName() {
        return defaultModel;
    }

    /**
     * Resolve the model name for a given agent and scenario.
     *
     * <p>Currently delegates to {@link #getDefaultModelName()}, but is designed
     * to support per-agent or per-scenario model overrides in the future (e.g.
     * via a configuration map or external registry).
     *
     * @param agentName the agent requesting the model (may be null)
     * @param scenario  the usage scenario (may be null)
     * @return the resolved model name
     */
    public String resolveModelName(String agentName, String scenario) {
        // Future: look up agentName/scenario in a model mapping.
        return getDefaultModelName();
    }
}
