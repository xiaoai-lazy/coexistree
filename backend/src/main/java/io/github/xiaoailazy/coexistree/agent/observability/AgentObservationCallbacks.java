package io.github.xiaoailazy.coexistree.agent.observability;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.adk.agents.CallbackContext;
import com.google.adk.agents.Callbacks;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import com.google.adk.tools.BaseTool;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import io.github.xiaoailazy.coexistree.observability.model.ModelDescriptorResolver;
import io.github.xiaoailazy.coexistree.observability.service.ObservationSpanService;
import io.github.xiaoailazy.coexistree.observability.context.SpanContextRegistry;
import io.reactivex.rxjava3.core.Maybe;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class AgentObservationCallbacks {

    private final ObservationSpanService spanService;
    private final SpanContextRegistry registry;
    private final ObjectMapper objectMapper;
    private final ModelDescriptorResolver modelDescriptorResolver;

    public AgentObservationCallbacks(ObservationSpanService spanService,
                                     SpanContextRegistry registry,
                                     ObjectMapper objectMapper,
                                     ModelDescriptorResolver modelDescriptorResolver) {
        this.spanService = spanService;
        this.registry = registry;
        this.objectMapper = objectMapper;
        this.modelDescriptorResolver = modelDescriptorResolver;
    }

    public Callbacks.BeforeAgentCallback createBeforeAgentCallback() {
        return ctx -> {
            try {
                String agentName = ctx.agentName();
                String invocationId = ctx.invocationId();

                // ===== 第一步：打印当前上下文 =====
                Map<String, Object> stateKeys = dumpStateKeys(ctx.state());
                log.info("[obs][beforeAgent][ctx] agent={}, invocationId={}, stateKeys={}",
                        agentName, invocationId, stateKeys);

                String conversationId = (String) ctx.state().get("user:conversationId");
                String runId = (String) ctx.state().get("temp:runId");
                LocalDateTime startedAt = LocalDateTime.now(ZoneOffset.UTC);

                if (runId == null) {
                    log.warn("[obs][beforeAgent][ctx] MISSING temp:runId! agent={}, stateKeys={}", agentName, stateKeys.keySet());
                    return Maybe.empty();
                }

                String traceId = runId;
                String parentSpanId = registry.findLatestActiveSpanId(runId);
                int depth = computeDepth(parentSpanId);

                String spanId;
                if ("root-agent".equals(agentName)) {
                    spanId = spanService.startRunSpan(runId, traceId, conversationId,
                            agentName, null, startedAt);
                    log.info("[obs][beforeAgent] run start, runId={}, conv={}, agent={}, parent={}, depth={}",
                            runId, conversationId, agentName, parentSpanId, depth);
                } else {
                    spanId = spanService.startAgentSpan(runId, traceId, conversationId,
                            agentName, parentSpanId, depth, null, startedAt);
                    log.info("[obs][beforeAgent] agent enter, runId={}, conv={}, agent={}, parent={}, depth={}",
                            runId, conversationId, agentName, parentSpanId, depth);
                }

                registry.registerInvocationSpan(runId, invocationId, spanId, parentSpanId, depth);
            } catch (Exception e) {
                log.error("[obs][beforeAgent] callback failed, agent={}", ctx.agentName(), e);
            }
            return Maybe.empty();
        };
    }

    public Callbacks.AfterAgentCallback createAfterAgentCallback() {
        return ctx -> {
            try {
                String agentName = ctx.agentName();
                String invocationId = ctx.invocationId();

                Map<String, Object> stateKeys = dumpStateKeys(ctx.state());
                String runId = (String) ctx.state().get("temp:runId");

                log.info("[obs][afterAgent][ctx] agent={}, invocationId={}, runId={}, stateKeys={}",
                        agentName, invocationId, runId, stateKeys.keySet());

                if (runId == null) {
                    log.warn("[obs][afterAgent][ctx] MISSING temp:runId! agent={}, stateKeys={}", agentName, stateKeys.keySet());
                    return Maybe.empty();
                }

                // Finish any unfinished model span from this invocation.
                // The LangChain4j streaming adapter does NOT pass token usage metadata
                // in afterModel callbacks, so we fall back to completing the model span here.
                String modelInvocationId = invocationId + ":model";
                String modelSpanId = registry.findSpanIdByInvocation(runId, modelInvocationId);
                if (modelSpanId != null) {
                    long modelDurationMs = computeDurationForSpan(modelSpanId);
                    spanService.finishSpanSuccess(modelSpanId, null, null, modelDurationMs);
                    registry.removeInvocation(runId, modelInvocationId);
                    log.info("[obs][afterAgent] finished model span from fallback, spanId={}, durationMs={}",
                            modelSpanId, modelDurationMs);
                }

                String spanId = registry.findSpanIdByInvocation(runId, invocationId);
                if (spanId == null) {
                    log.warn("[obs][afterAgent] no span for runId={}, invocationId={}", runId, invocationId);
                    return Maybe.empty();
                }

                long durationMs = computeDurationForSpan(spanId);
                spanService.finishSpanSuccess(spanId, null, null, durationMs);
                registry.removeInvocation(runId, invocationId);
                log.info("[obs][afterAgent] agent exit, runId={}, agent={}, durationMs={}",
                        runId, agentName, durationMs);
            } catch (Exception e) {
                log.error("[obs][afterAgent] callback failed, agent={}", ctx.agentName(), e);
            }
            return Maybe.empty();
        };
    }

    public Callbacks.BeforeModelCallback createBeforeModelCallback() {
        return (ctx, llmRequestBuilder) -> {
            try {
                String agentName = ctx.agentName();
                String invocationId = ctx.invocationId();

                Map<String, Object> stateKeys = dumpStateKeys(ctx.state());
                log.info("[obs][beforeModel][ctx] agent={}, invocationId={}, stateKeys={}",
                        agentName, invocationId, stateKeys.keySet());

                String runId = (String) ctx.state().get("temp:runId");
                String conversationId = (String) ctx.state().get("user:conversationId");
                LocalDateTime startedAt = LocalDateTime.now(ZoneOffset.UTC);

                if (runId == null) {
                    log.warn("[obs][beforeModel][ctx] MISSING temp:runId! agent={}, stateKeys={}", agentName, stateKeys.keySet());
                    return Maybe.empty();
                }

                String parentSpanId = registry.findSpanIdByInvocation(runId, invocationId);
                if (parentSpanId == null) {
                    log.warn("[obs][beforeModel][ctx] MISSING parent span! runId={}, invocationId={}", runId, invocationId);
                    return Maybe.empty();
                }

                // Streaming: beforeModel may fire per-chunk. Only create ONE span.
                String modelInvocationId = invocationId + ":model";
                String existingSpanId = registry.findSpanIdByInvocation(runId, modelInvocationId);
                if (existingSpanId != null) {
                    log.debug("[obs][beforeModel] span already exists for streaming, reusing spanId={}", existingSpanId);
                    return Maybe.empty();
                }

                String modelName = extractModelName(llmRequestBuilder);
                String spanId = spanService.startModelSpan(runId, runId, conversationId,
                        agentName, modelName, parentSpanId, null, startedAt);

                // Serialize full LlmRequest to JSON
                LlmRequest request = null;
                try {
                    request = llmRequestBuilder.build();
                    Map<String, Object> requestMap = new HashMap<>();

                    // contents: all messages with roles and structured parts
                    if (request.contents() != null) {
                        requestMap.put("contents", serializeContents(request.contents()));
                    }

                    // config: temperature, topP, topK, maxOutputTokens, stopSequences
                    Map<String, Object> configMap = new HashMap<>();
                    llmRequestBuilder.config().ifPresent(config -> {
                        config.temperature().ifPresent(v -> configMap.put("temperature", v));
                        config.topP().ifPresent(v -> configMap.put("topP", v));
                        config.topK().ifPresent(v -> configMap.put("topK", v));
                        config.maxOutputTokens().ifPresent(v -> configMap.put("maxOutputTokens", v));
                        config.stopSequences().ifPresent(v -> configMap.put("stopSequences", v));
                    });
                    if (!configMap.isEmpty()) {
                        requestMap.put("config", configMap);
                    }

                    String requestJson = objectMapper.writeValueAsString(requestMap);
                    spanService.setInputPayload(spanId, requestJson);
                } catch (Exception e) {
                    log.warn("[obs][beforeModel] failed to store request for spanId={}", spanId, e);
                }

                registry.registerInvocationSpan(runId, modelInvocationId, spanId, parentSpanId, 0);
                log.info("[obs][beforeModel] model call, runId={}, conv={}, agent={}, model={}, parent={}, contentCount={}",
                        runId, conversationId, agentName, modelName, parentSpanId,
                        request != null && request.contents() != null ? request.contents().size() : 0);
            } catch (Exception e) {
                log.error("[obs][beforeModel] callback failed, agent={}", ctx.agentName(), e);
            }
            return Maybe.empty();
        };
    }

    public Callbacks.AfterModelCallback createAfterModelCallback() {
        return (ctx, llmResponse) -> {
            try {
                String agentName = ctx.agentName();
                String invocationId = ctx.invocationId() + ":model";

                Map<String, Object> stateKeys = dumpStateKeys(ctx.state());
                String runId = (String) ctx.state().get("temp:runId");

                log.info("[obs][afterModel][ctx] agent={}, invocationId={}, runId={}, stateKeys={}",
                        agentName, invocationId, runId, stateKeys.keySet());

                if (runId == null) {
                    log.warn("[obs][afterModel][ctx] MISSING temp:runId! agent={}, stateKeys={}", agentName, stateKeys.keySet());
                    return Maybe.empty();
                }

                String spanId = registry.findSpanIdByInvocation(runId, invocationId);
                if (spanId == null) {
                    // In streaming mode, afterAgent may finish the model span before
                    // late afterModel chunks arrive. This is expected.
                    log.debug("[obs][afterModel] no span for runId={}, invocationId={} (likely streaming, span already closed)",
                            runId, invocationId);
                    return Maybe.empty();
                }

                // Detect streaming: if no token usage metadata, this is an intermediate chunk
                boolean hasUsage = llmResponse != null
                        && llmResponse.usageMetadata().isPresent()
                        && llmResponse.usageMetadata().get().totalTokenCount().isPresent();

                if (!hasUsage) {
                    log.debug("[obs][afterModel] streaming chunk, skipping span finish for spanId={}", spanId);
                    return Maybe.empty();
                }

                // Final chunk (has token stats) — finish the span
                long durationMs = computeDurationForSpan(spanId);

                Map<String, Object> attributes = new HashMap<>();
                Map<String, Object> responseMap = new HashMap<>();

                if (llmResponse != null) {
                    // Full response content with role and parts
                    llmResponse.content().ifPresent(content -> {
                        Map<String, Object> contentMap = new HashMap<>();
                        content.role().ifPresent(role -> contentMap.put("role", role));
                        if (content.parts() != null && content.parts().isPresent()) {
                            contentMap.put("parts", serializeParts(content.parts().get()));
                        }
                        responseMap.put("content", contentMap);
                    });

                    // candidates() and promptFeedback() require ADK methods not available in current version
                    // TODO: re-enable when ADK library provides these methods

                    llmResponse.modelVersion().ifPresent(v -> attributes.put("modelVersion", v));

                    // Token stats with renamed keys
                    llmResponse.usageMetadata().ifPresent(usage -> {
                        usage.promptTokenCount().ifPresent(v -> attributes.put("tokenPrompt", v));
                        usage.candidatesTokenCount().ifPresent(v -> attributes.put("tokenCompletion", v));
                        usage.totalTokenCount().ifPresent(v -> attributes.put("tokenTotal", v));
                        usage.thoughtsTokenCount().ifPresent(v -> attributes.put("tokenThoughts", v));
                    });
                }

                Map<String, Object> outputPayload = responseMap.isEmpty() ? null : responseMap;
                spanService.finishSpanSuccess(spanId, outputPayload, attributes, durationMs);
                registry.removeInvocation(runId, invocationId);
                Object tokenTotal = attributes.get("tokenTotal");
                log.info("[obs][afterModel] model done, runId={}, agent={}, durationMs={}, tokens={}",
                        runId, agentName, durationMs, tokenTotal != null ? tokenTotal : "N/A");
            } catch (Exception e) {
                log.error("[obs][afterModel] callback failed, agent={}", ctx.agentName(), e);
            }
            return Maybe.empty();
        };
    }

    public Callbacks.OnModelErrorCallback createOnModelErrorCallback() {
        return (ctx, llmRequest, error) -> {
            try {
                String agentName = ctx.agentName();
                String invocationId = ctx.invocationId() + ":model";

                Map<String, Object> stateKeys = dumpStateKeys(ctx.state());
                String runId = (String) ctx.state().get("temp:runId");
                String conversationId = (String) ctx.state().get("user:conversationId");

                log.warn("[obs][onModelError][ctx] agent={}, invocationId={}, runId={}, stateKeys={}",
                        agentName, invocationId, runId, stateKeys.keySet());

                if (runId == null) {
                    log.warn("[obs][onModelError][ctx] MISSING temp:runId!");
                    return Maybe.empty();
                }

                String spanId = registry.findSpanIdByInvocation(runId, invocationId);
                if (spanId == null) {
                    log.warn("[obs][onModelError][ctx] MISSING span! runId={}, invocationId={}, errorType={}",
                            runId, invocationId, error != null ? error.getClass().getName() : "null");
                    return Maybe.empty();
                }

                long durationMs = computeDurationForSpan(spanId);
                Map<String, Object> errorPayload = null;
                if (error != null) {
                    errorPayload = new HashMap<>();
                    errorPayload.put("type", error.getClass().getName());
                    errorPayload.put("message", error.getMessage());
                    errorPayload.put("stackTrace", getStackTrace(error));

                    // Extract HTTP status code if ApiException
                    try {
                        if (error instanceof com.google.api.gax.rpc.ApiException apiEx) {
                            var statusCode = apiEx.getStatusCode();
                            if (statusCode != null) {
                                errorPayload.put("statusCode", statusCode.getCode());
                            }
                        }
                    } catch (Exception e) {
                        log.debug("[obs][onModelError] failed to extract status code", e);
                    }
                }

                spanService.finishSpanFailed(spanId, errorPayload, durationMs);
                registry.removeInvocation(runId, invocationId);

                log.error("[obs][onModelError] model failed, runId={}, conv={}, agent={}, errorType={}, errorMsg={}, durationMs={}",
                        runId, conversationId, agentName,
                        error != null ? error.getClass().getName() : "null",
                        error != null ? error.getMessage() : "null",
                        durationMs, error);
            } catch (Exception e) {
                log.error("[obs][onModelError] callback failed, agent={}", ctx.agentName(), e);
            }
            return Maybe.empty();
        };
    }

    public Callbacks.BeforeToolCallback createBeforeToolCallback() {
        return (invocationCtx, baseTool, input, toolContext) -> {
            try {
                if (isAgentTool(baseTool)) {
                    log.debug("[obs][beforeTool] skipping AgentTool: {}", baseTool.name());
                    return Maybe.empty();
                }

                String toolName = baseTool.name();
                String agentName = invocationCtx.agent().name();
                String invocationId = invocationCtx.invocationId();

                var toolState = toolContext.state();
                String runId = toolState != null ? toString(toolState.get("temp:runId")) : null;
                String conversationId = toolState != null ? toString(toolState.get("user:conversationId")) : null;
                String systemId = toolState != null ? toString(toolState.get("user:systemId")) : null;
                LocalDateTime startedAt = LocalDateTime.now(ZoneOffset.UTC);

                if (runId == null) {
                    log.warn("[obs][beforeTool][ctx] MISSING temp:runId! tool={}", toolName);
                    return Maybe.empty();
                }

                String parentSpanId = registry.findSpanIdByInvocation(runId, invocationId);
                if (parentSpanId == null) {
                    log.warn("[obs][beforeTool][ctx] MISSING parent span! runId={}, invocationId={}", runId, invocationId);
                    return Maybe.empty();
                }

                String spanId = spanService.startToolSpan(runId, runId, conversationId,
                        agentName, toolName, parentSpanId, null, startedAt);

                // Serialize full tool input args
                if (input != null) {
                    try {
                        Map<String, Object> inputPayload = new HashMap<>();
                        inputPayload.put("toolName", toolName);
                        inputPayload.put("args", objectMapper.convertValue(input, new TypeReference<Map<String, Object>>() {}));
                        String inputJson = objectMapper.writeValueAsString(inputPayload);
                        spanService.setInputPayload(spanId, inputJson);
                    } catch (Exception e) {
                        log.warn("[obs][beforeTool] failed to serialize input for spanId={}", spanId, e);
                        try {
                            String fallbackJson = objectMapper.writeValueAsString(
                                    Map.of("toolName", toolName, "argsRaw", input.toString()));
                            spanService.setInputPayload(spanId, fallbackJson);
                        } catch (Exception e2) {
                            log.warn("[obs][beforeTool] fallback serialization also failed for spanId={}", spanId, e2);
                        }
                    }
                }

                registry.registerInvocationSpan(runId, invocationId + ":tool", spanId, parentSpanId, 0);
                log.info("[obs][beforeTool] tool call, runId={}, agent={}, tool={}, systemId={}, parent={}",
                        runId, agentName, toolName, systemId, parentSpanId);
            } catch (Exception e) {
                log.error("[obs][beforeTool] callback failed, tool={}", baseTool.name(), e);
            }
            return Maybe.empty();
        };
    }

    public Callbacks.AfterToolCallback createAfterToolCallback() {
        return (invocationCtx, baseTool, input, toolContext, response) -> {
            try {
                if (isAgentTool(baseTool)) return Maybe.empty();

                String toolName = baseTool.name();
                String invocationId = invocationCtx.invocationId() + ":tool";

                var toolState = toolContext.state();
                String runId = toolState != null ? toString(toolState.get("temp:runId")) : null;

                if (runId == null) {
                    log.warn("[obs][afterTool] MISSING temp:runId! tool={}", toolName);
                    return Maybe.empty();
                }

                String spanId = registry.findSpanIdByInvocation(runId, invocationId);
                if (spanId == null) {
                    log.warn("[obs][afterTool] no span for runId={}, invocationId={}", runId, invocationId);
                    return Maybe.empty();
                }

                long durationMs = computeDurationForSpan(spanId);

                // Serialize full tool response
                Map<String, Object> outputPayload = null;
                if (response != null) {
                    try {
                        outputPayload = objectMapper.convertValue(response, new TypeReference<Map<String, Object>>() {});
                    } catch (Exception e) {
                        log.warn("[obs][afterTool] failed to serialize response for spanId={}, falling back to raw", spanId, e);
                        outputPayload = Map.of("resultRaw", response.toString());
                    }
                }

                spanService.finishSpanSuccess(spanId, outputPayload, null, durationMs);
                registry.removeInvocation(runId, invocationId);
                log.info("[obs][afterTool] tool done, runId={}, tool={}, durationMs={}",
                        runId, toolName, durationMs);
            } catch (Exception e) {
                log.error("[obs][afterTool] callback failed, tool={}", baseTool.name(), e);
            }
            return Maybe.empty();
        };
    }

    public Callbacks.OnToolErrorCallback createOnToolErrorCallback() {
        return (invocationCtx, baseTool, input, toolContext, error) -> {
            try {
                if (isAgentTool(baseTool)) return Maybe.empty();

                String toolName = baseTool.name();
                String agentName = invocationCtx.agent().name();
                String invocationId = invocationCtx.invocationId() + ":tool";

                var toolState = toolContext.state();
                String runId = toolState != null ? toString(toolState.get("temp:runId")) : null;
                String conversationId = toolState != null ? toString(toolState.get("user:conversationId")) : null;

                if (runId == null) {
                    log.warn("[obs][onToolError][ctx] MISSING temp:runId!");
                    return Maybe.empty();
                }

                String spanId = registry.findSpanIdByInvocation(runId, invocationId);
                if (spanId == null) {
                    log.warn("[obs][onToolError][ctx] MISSING span! runId={}, invocationId={}", runId, invocationId);
                    return Maybe.empty();
                }

                long durationMs = computeDurationForSpan(spanId);

                Map<String, Object> errorPayload = null;
                if (error != null) {
                    errorPayload = new HashMap<>();
                    errorPayload.put("type", error.getClass().getName());
                    errorPayload.put("message", error.getMessage());
                    errorPayload.put("stackTrace", getStackTrace(error));
                }

                spanService.finishSpanFailed(spanId, errorPayload, durationMs);
                registry.removeInvocation(runId, invocationId);

                log.error("[obs][onToolError] tool failed, runId={}, agent={}, tool={}, errorType={}, errorMsg={}, durationMs={}",
                        runId, agentName, toolName,
                        error != null ? error.getClass().getName() : "null",
                        error != null ? error.getMessage() : "null",
                        durationMs);
            } catch (Exception e) {
                log.error("[obs][onToolError] callback failed, tool={}", baseTool.name(), e);
            }
            return Maybe.empty();
        };
    }

    // -- Serialization helpers for LLM data --

    private List<Map<String, Object>> serializeContents(List<Content> contents) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Content content : contents) {
            if (content == null) continue;
            Map<String, Object> messageMap = new HashMap<>();
            content.role().ifPresent(role -> messageMap.put("role", role));
            if (content.parts() != null && content.parts().isPresent()) {
                messageMap.put("parts", serializeParts(content.parts().get()));
            }
            result.add(messageMap);
        }
        return result;
    }

    private List<Map<String, Object>> serializeParts(List<Part> parts) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Part part : parts) {
            if (part == null) continue;
            Map<String, Object> partMap = new HashMap<>();

            var textOpt = part.text();
            if (textOpt != null && textOpt.isPresent()) {
                partMap.put("type", "text");
                partMap.put("text", textOpt.get());
                result.add(partMap);
                continue;
            }

            var fcOpt = part.functionCall();
            if (fcOpt != null && fcOpt.isPresent()) {
                var fc = fcOpt.get();
                Map<String, Object> fcMap = new HashMap<>();
                fcMap.put("type", "functionCall");
                fcMap.put("name", fc.name());
                fc.args().ifPresent(args -> fcMap.put("args", objectMapper.convertValue(args, new TypeReference<Map<String, Object>>() {})));
                result.add(fcMap);
                continue;
            }

            var frOpt = part.functionResponse();
            if (frOpt != null && frOpt.isPresent()) {
                var fr = frOpt.get();
                Map<String, Object> frMap = new HashMap<>();
                frMap.put("type", "functionResponse");
                frMap.put("name", fr.name());
                fr.response().ifPresent(resp -> frMap.put("response", objectMapper.convertValue(resp, new TypeReference<Map<String, Object>>() {})));
                result.add(frMap);
                continue;
            }

            partMap.put("type", "unknown");
            partMap.put("raw", part.toString());
            result.add(partMap);
        }
        return result;
    }

    private String getStackTrace(Throwable t) {
        if (t == null) return "";
        java.io.StringWriter sw = new java.io.StringWriter();
        t.printStackTrace(new java.io.PrintWriter(sw));
        return sw.toString();
    }

    // -- Internal helpers --

    /**
     * Dump all state keys and their values for debugging context loss.
     * Null-safe: if state is null, returns a map with a single "state=null" entry.
     */
    private Map<String, Object> dumpStateKeys(Map<String, Object> state) {
        if (state == null) {
            return Map.of("state", "null");
        }
        try {
            Map<String, Object> snapshot = new java.util.LinkedHashMap<>();
            for (var entry : state.entrySet()) {
                Object val = entry.getValue();
                snapshot.put(entry.getKey(), val != null ? val.toString() : "null");
            }
            return snapshot;
        } catch (Exception e) {
            return Map.of("error", "failed to dump state: " + e.getMessage());
        }
    }

    private int computeDepth(String parentSpanId) {
        if (parentSpanId == null) return 0;
        var ctx = registry.findSpanRuntime(parentSpanId);
        return ctx != null ? ctx.depth() + 1 : 1;
    }

    private long computeDurationForSpan(String spanId) {
        var ctx = registry.findSpanRuntime(spanId);
        if (ctx != null) {
            return java.time.Duration.between(ctx.registeredAt(), LocalDateTime.now()).toMillis();
        }
        return 0;
    }

    private boolean isAgentTool(BaseTool baseTool) {
        String className = baseTool.getClass().getSimpleName();
        return "AgentTool".equals(className) || baseTool.getClass().getName().contains(".AgentTool");
    }

    private String extractModelName(LlmRequest.Builder builder) {
        return modelDescriptorResolver.getDefaultModelName();
    }

    private static String toString(Object obj) {
        return obj != null ? obj.toString() : null;
    }
}
