package io.github.xiaoailazy.coexistree.shared.aspect;

import io.github.xiaoailazy.coexistree.indexer.llm.LlmClient;
import io.github.xiaoailazy.coexistree.shared.entity.LlmCallEntity;
import io.github.xiaoailazy.coexistree.shared.repository.LlmCallRepository;
import io.github.xiaoailazy.coexistree.shared.util.LlmCallContext;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * AOP aspect that intercepts all LLM calls and persists tracking data.
 * Captures: model, temperature, token usage, elapsed time, success/failure.
 * Scenario metadata comes from LlmCallContext (explicit) or StackWalker (fallback).
 */
@Slf4j
@Aspect
@Component
public class LlmCallTrackingAspect {

    private final LlmCallRepository llmCallRepository;

    public LlmCallTrackingAspect(LlmCallRepository llmCallRepository) {
        this.llmCallRepository = llmCallRepository;
    }

    /**
     * Intercepts all public methods of LlmClient that return LlmResponse.
     * This covers both chat(...) overloads.
     */
    @Around("execution(io.github.xiaoailazy.coexistree.indexer.llm.LlmClient.LlmResponse "
            + "io.github.xiaoailazy.coexistree.indexer.llm.LlmClient.chat(..))")
    public Object trackLlmCall(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();
        Object[] args = joinPoint.getArgs();

        // Extract call parameters
        String prompt = args.length > 0 ? (String) args[0] : "";
        String model = args.length > 1 ? resolveStringArg(args[1]) : null;
        Double temperature = args.length > 2 ? resolveDoubleArg(args[2]) : null;

        // Resolve scenario: explicit context first, then fallback
        String scenario = resolveScenario();
        if (scenario == null || scenario.isBlank()) {
            scenario = detectScenarioFromStack();
        }
        if (scenario == null || scenario.isBlank()) {
            scenario = "UNKNOWN";
        }

        LlmCallEntity entity = new LlmCallEntity();
        entity.setScenario(scenario);
        entity.setModel(resolveModel(model));
        entity.setTemperature(temperature);
        entity.setCreatedAt(LocalDateTime.now());

        // Populate context metadata if available
        LlmCallContext.get().ifPresent(info -> {
            if (info.documentId() != null) entity.setDocumentId(info.documentId());
            if (info.systemId() != null) entity.setSystemId(info.systemId());
            if (info.userId() != null) entity.setUserId(info.userId());
        });

        try {
            LlmClient.LlmResponse response = (LlmClient.LlmResponse) joinPoint.proceed();
            long elapsed = System.currentTimeMillis() - startTime;

            // Check if response indicates an error
            String content = response.content();
            boolean isError = content != null && content.startsWith("Error: ");

            entity.setElapsedMs(elapsed);
            entity.setSuccess(!isError);
            if (isError) {
                entity.setErrorMessage(content);
            }

            // Extract usage
            if (response.usage() != null) {
                entity.setInputTokens(response.usage().inputTokens());
                entity.setOutputTokens(response.usage().outputTokens());
                entity.setTotalTokens(response.usage().totalTokens());
                entity.setReasoningTokens(response.usage().reasoningTokens());
            }

            persist(entity);
            return response;

        } catch (Throwable e) {
            long elapsed = System.currentTimeMillis() - startTime;
            entity.setElapsedMs(elapsed);
            entity.setSuccess(false);
            entity.setErrorMessage(e.getMessage());
            persist(entity);
            throw e;
        } finally {
            LlmCallContext.clear();
        }
    }

    private String resolveStringArg(Object arg) {
        return arg instanceof String s ? s : null;
    }

    private Double resolveDoubleArg(Object arg) {
        return arg instanceof Double d ? d : null;
    }

    private String resolveModel(String model) {
        return (model != null && !model.isBlank()) ? model : "unknown";
    }

    private String resolveScenario() {
        return LlmCallContext.get().map(LlmCallContext.LlmCallInfo::scenario).orElse(null);
    }

    private String detectScenarioFromStack() {
        return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)
                .walk(frames -> frames
                        .filter(f -> !isInternalClass(f.getClassName()))
                        .findFirst()
                        .map(f -> deriveScenarioFromClassAndMethod(f.getClassName(), f.getMethodName()))
                        .orElse(null));
    }

    private boolean isInternalClass(String className) {
        return className.contains("LlmClient")
                || className.contains("RetryableLlmService")
                || className.contains(".aspect.");
    }

    private String deriveScenarioFromClassAndMethod(String className, String methodName) {
        String simpleClass = className.substring(className.lastIndexOf('.') + 1);
        // Strip common suffixes
        String baseName = simpleClass
                .replaceFirst("Impl$", "")
                .replaceFirst("Service$", "")
                .replaceFirst("Controller$", "");
        return baseName.toUpperCase() + "_" + methodName.toUpperCase();
    }

    private void persist(LlmCallEntity entity) {
        try {
            llmCallRepository.save(entity);
        } catch (Exception e) {
            log.warn("Failed to persist LLM call tracking: {}", e.getMessage());
        }
    }
}
