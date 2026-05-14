package io.github.xiaoailazy.coexistree.knowledge.plan;

import io.github.xiaoailazy.coexistree.indexer.llm.LlmClient;
import io.github.xiaoailazy.coexistree.shared.enums.ErrorCode;
import io.github.xiaoailazy.coexistree.shared.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeoutException;

/**
 * LLM 生成更新计划，带 1,1,2 秒斐波那契退避，最多 4 次尝试（设计 §6.A-7、§16.5）。
 */
@Slf4j
@Service
public class SystemTreePlanServiceImpl implements SystemTreePlanService {

    private static final int[] BACKOFF_MS = {1000, 1000, 2000};

    private final LlmClient llmClient;
    private final FibonacciSleeper fibonacciSleeper;

    public SystemTreePlanServiceImpl(LlmClient llmClient, FibonacciSleeper fibonacciSleeper) {
        this.llmClient = llmClient;
        this.fibonacciSleeper = fibonacciSleeper;
    }

    @Override
    public String generateUpdatePlanJson(String prompt) {
        for (int i = 0; i < 4; i++) {
            String content;
            try {
                LlmClient.LlmResponse response = llmClient.chat(prompt, null, 0.0);
                content = response != null ? response.content() : "";
            } catch (RuntimeException e) {
                if (isTimeout(e)) {
                    content = "";
                    log.warn("LLM plan call timeout, attempt={}", i + 1, e);
                } else {
                    throw e;
                }
            }
            if (!content.isBlank() && !content.startsWith("Error:")) {
                return content;
            }
            if (i == 3) {
                throw new BusinessException(ErrorCode.LLM_RETRIES_EXHAUSTED, "LLM plan generation failed after retries");
            }
            sleepBackoff(i);
        }
        throw new BusinessException(ErrorCode.LLM_RETRIES_EXHAUSTED, "LLM plan generation failed after retries");
    }

    private static boolean isTimeout(Throwable e) {
        for (Throwable c = e; c != null; c = c.getCause()) {
            if (c instanceof TimeoutException) {
                return true;
            }
        }
        return false;
    }

    private void sleepBackoff(int failureIndex) {
        try {
            fibonacciSleeper.sleepMillis(BACKOFF_MS[failureIndex]);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.LLM_RETRIES_EXHAUSTED, "Interrupted during LLM retry backoff");
        }
    }
}
