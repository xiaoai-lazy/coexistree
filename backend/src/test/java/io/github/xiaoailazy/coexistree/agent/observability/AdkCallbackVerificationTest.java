package io.github.xiaoailazy.coexistree.agent.observability;

import com.google.adk.agents.CallbackContext;
import com.google.adk.agents.Callbacks;
import com.google.adk.agents.InvocationContext;
import com.google.adk.agents.LlmAgent;
import com.google.adk.agents.ReadonlyContext;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import com.google.adk.tools.BaseTool;
import com.google.adk.tools.ToolContext;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponseUsageMetadata;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Maybe;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verification tests for ADK callback API capabilities.
 *
 * Each test verifies a specific technical premise needed for the session audit event tree feature.
 * These are compile-time + runtime checks that validate the ADK Java 1.0.0 API.
 */
@Slf4j
class AdkCallbackVerificationTest {

    // ===== V1: CallbackContext provides sessionId(), agentName(), invocationId() =====

    @Test
    @DisplayName("V1: CallbackContext provides sessionId(), agentName(), invocationId() via ReadonlyContext")
    void verifyCallbackContextProvidesSessionId() {
        // Verify that ReadonlyContext (parent of CallbackContext) has sessionId()
        // This is critical: the design assumed CallbackContext had no sessionId(), but it does.
        Callbacks.BeforeAgentCallback callback = ctx -> {
            String sessionId = ctx.sessionId();
            String agentName = ctx.agentName();
            String invocationId = ctx.invocationId();
            log.info("sessionId='{}', agentName='{}', invocationId='{}'", sessionId, agentName, invocationId);
            return Maybe.empty();
        };

        assertThat(callback).isNotNull();
    }

    // ===== V2: beforeModelCallback receives LlmRequest.Builder (ADK type, not LangChain4j) =====

    @Test
    @DisplayName("V2: beforeModelCallback receives LlmRequest.Builder (ADK type)")
    void verifyBeforeModelCallbackSignature() {
        Callbacks.BeforeModelCallback callback = (ctx, llmRequestBuilder) -> {
            // LlmRequest.Builder is from ADK (com.google.adk.models.LlmRequest)
            String builderType = llmRequestBuilder.getClass().getName();
            log.info("beforeModelCallback: agentName='{}', builderType={}", ctx.agentName(), builderType);
            return Maybe.empty();
        };

        assertThat(callback).isNotNull();
    }

    // ===== V3: afterModelCallback receives LlmResponse (ADK type, not LangChain4j) =====

    @Test
    @DisplayName("V3: afterModelCallback receives LlmResponse (ADK type)")
    void verifyAfterModelCallbackSignature() {
        Callbacks.AfterModelCallback callback = (ctx, llmResponse) -> {
            // LlmResponse is from ADK (com.google.adk.models.LlmResponse)
            String responseType = llmResponse.getClass().getName();
            log.info("afterModelCallback: agentName='{}', responseType={}", ctx.agentName(), responseType);
            return Maybe.empty();
        };

        assertThat(callback).isNotNull();
    }

    // ===== V4: beforeToolCallback receives InvocationContext with session().id() =====

    @Test
    @DisplayName("V4: beforeToolCallback receives InvocationContext with session().id()")
    void verifyBeforeToolCallbackReceivesInvocationContext() {
        Callbacks.BeforeToolCallback callback = (invocationCtx, baseTool, input, toolContext) -> {
            // InvocationContext has session().id()
            String sessionId = invocationCtx.session().id();
            String agentName = invocationCtx.agent().name();
            log.info("beforeToolCallback: sessionId='{}', agentName='{}'", sessionId, agentName);
            return Maybe.empty();
        };

        assertThat(callback).isNotNull();
    }

    // ===== V5: CallbackContext.state() provides State (not Map) =====

    @Test
    @DisplayName("V5: CallbackContext.state() returns State type")
    void verifyCallbackContextProvidesStateAccess() {
        Callbacks.BeforeAgentCallback callback = ctx -> {
            // CallbackContext.state() returns com.google.adk.sessions.State (not Map)
            // But ReadonlyContext.state() returns Map<String, Object>
            Object stateFromReadonly = ctx.state();
            String stateType = stateFromReadonly != null ? stateFromReadonly.getClass().getName() : "null";
            log.info("CallbackContext.state() type: {}", stateType);
            return Maybe.empty();
        };

        assertThat(callback).isNotNull();
    }

    // ===== V6: LlmAgent accepts all callback types via builder =====

    @Test
    @DisplayName("V6: LlmAgent accepts all callback types via builder")
    void verifyLlmAgentAcceptsAllCallbacks() {
        LlmAgent agent = LlmAgent.builder()
                .name("verify-callback-agent")
                .model("gpt-4o-mini")
                .instruction("test")
                .tools(List.of())
                .beforeAgentCallback(ctx -> Maybe.empty())
                .afterAgentCallback(ctx -> Maybe.empty())
                .beforeModelCallback((ctx, req) -> Maybe.empty())
                .afterModelCallback((ctx, resp) -> Maybe.empty())
                .onModelErrorCallback((ctx, req, err) -> Maybe.empty())
                .beforeToolCallback((ctx, tool, input, toolCtx) -> Maybe.empty())
                .afterToolCallback((ctx, tool, input, toolCtx, resp) -> Maybe.empty())
                .onToolErrorCallback((ctx, tool, input, toolCtx, err) -> Maybe.empty())
                .build();

        assertThat(agent).isNotNull();
        assertThat(agent.name()).isEqualTo("verify-callback-agent");
        assertThat(agent.beforeModelCallback()).isNotNull();
        assertThat(agent.afterModelCallback()).isNotNull();
        assertThat(agent.beforeToolCallback()).isNotNull();
        assertThat(agent.afterToolCallback()).isNotNull();
        assertThat(agent.beforeAgentCallback()).isNotNull();
        assertThat(agent.afterAgentCallback()).isNotNull();
    }

    // ===== V7: Multiple agents can share the same callback instance =====

    @Test
    @DisplayName("V7: Multiple agents can share the same callback instance")
    void verifyCallbacksAreShareable() {
        // Create callbacks once, reuse for multiple agents
        Callbacks.BeforeAgentCallback sharedBeforeAgent = ctx -> {
            log.info("Shared callback: agentName='{}', sessionId='{}'", ctx.agentName(), ctx.sessionId());
            return Maybe.empty();
        };

        LlmAgent agent1 = LlmAgent.builder()
                .name("agent-1")
                .model("gpt-4o-mini")
                .instruction("agent 1")
                .tools(List.of())
                .beforeAgentCallback(sharedBeforeAgent)
                .build();

        LlmAgent agent2 = LlmAgent.builder()
                .name("agent-2")
                .model("gpt-4o-mini")
                .instruction("agent 2")
                .tools(List.of())
                .beforeAgentCallback(sharedBeforeAgent)
                .build();

        // Note: LlmAgent stores callbacks in ImmutableList wrappers, so the lists aren't
        // the same instance even when the underlying lambda is shared. The important
        // thing is that both agents can use the same callback logic.
        assertThat(agent1.beforeAgentCallback()).hasSize(1);
        assertThat(agent2.beforeAgentCallback()).hasSize(1);
    }

    // ===== V8: LlmResponse exposes usageMetadata and modelVersion =====

    @Test
    @DisplayName("V8: LlmResponse.usageMetadata() and modelVersion() are accessible")
    void verifyLlmResponseExposesUsageAndModel() {
        // Verify that ADK LlmResponse has usageMetadata() and modelVersion() methods.
        // These are needed to replace LlmCallTrackingAspect token stats.
        //
        // Compiled bytecode confirms:
        //   LlmResponse.usageMetadata() -> Optional<GenerateContentResponseUsageMetadata>
        //   LlmResponse.modelVersion()  -> Optional<String>

        Callbacks.AfterModelCallback callback = (ctx, llmResponse) -> {
            var usageOpt = llmResponse.usageMetadata();
            var modelVersionOpt = llmResponse.modelVersion();

            log.info("LlmResponse methods: usageMetadata={}, modelVersion={}",
                    usageOpt.getClass().getSimpleName(),
                    modelVersionOpt.getClass().getSimpleName());

            return Maybe.empty();
        };

        assertThat(callback).isNotNull();
    }

    @Test
    @DisplayName("V9: LlmRequest.Builder.config() exposes temperature")
    void verifyLlmRequestExposesConfigWithTemperature() {
        // Verify that ADK LlmRequest.Builder has config() method.
        // config() -> Optional<GenerateContentConfig>, config().temperature() -> Optional<Float>
        //
        // Compiled bytecode confirms:
        //   LlmRequest.Builder.config() -> Optional<GenerateContentConfig>
        //   GenerateContentConfig.temperature() -> Optional<Float>

        Callbacks.BeforeModelCallback callback = (ctx, builder) -> {
            var configOpt = builder.config();
            var temperatureOpt = configOpt.flatMap(GenerateContentConfig::temperature);

            log.info("LlmRequest.Builder.config exists: present={}", configOpt.isPresent());
            log.info("config.temperature exists: present={}", temperatureOpt.isPresent());

            return Maybe.empty();
        };

        assertThat(callback).isNotNull();
    }

    @Test
    @DisplayName("V10: GenerateContentResponseUsageMetadata has all token count fields")
    void verifyUsageMetadataHasAllTokenFields() {
        // Verify that GenerateContentResponseUsageMetadata has the fields we need
        // to replace LlmCallTrackingAspect token stats

        GenerateContentResponseUsageMetadata usage = GenerateContentResponseUsageMetadata.builder()
                .promptTokenCount(100)
                .candidatesTokenCount(50)
                .totalTokenCount(150)
                .thoughtsTokenCount(20)
                .build();

        assertThat(usage.promptTokenCount()).isPresent().hasValue(100);
        assertThat(usage.candidatesTokenCount()).isPresent().hasValue(50);
        assertThat(usage.totalTokenCount()).isPresent().hasValue(150);
        assertThat(usage.thoughtsTokenCount()).isPresent().hasValue(20);

        log.info("GenerateContentResponseUsageMetadata fields verified: "
                + "prompt={}, candidates={}, total={}, thoughts={}",
                usage.promptTokenCount().orElse(-1),
                usage.candidatesTokenCount().orElse(-1),
                usage.totalTokenCount().orElse(-1),
                usage.thoughtsTokenCount().orElse(-1));
    }
}
