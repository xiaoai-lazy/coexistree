package io.github.xiaoailazy.coexistree.knowledge.plan;

import io.github.xiaoailazy.coexistree.indexer.llm.LlmClient;
import io.github.xiaoailazy.coexistree.shared.enums.ErrorCode;
import io.github.xiaoailazy.coexistree.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemTreePlanServiceImplRetryTest {

    @Mock
    private LlmClient llmClient;

    @Mock
    private FibonacciSleeper fibonacciSleeper;

    @Test
    void retriesWithFibonacciBackoffThenSucceeds() throws Exception {
        String okJson = "{\"changeRecordId\":1,\"baseTreeVersion\":1,\"operations\":[]}";
        when(llmClient.chat(any(), any(), any()))
                .thenThrow(new RuntimeException(new TimeoutException("t1")))
                .thenThrow(new RuntimeException(new TimeoutException("t2")))
                .thenThrow(new RuntimeException(new TimeoutException("t3")))
                .thenReturn(new LlmClient.LlmResponse(null, okJson, null));

        SystemTreePlanServiceImpl svc = new SystemTreePlanServiceImpl(llmClient, fibonacciSleeper);
        assertThat(svc.generateUpdatePlanJson("prompt")).isEqualTo(okJson);

        verify(llmClient, times(4)).chat(eq("prompt"), eq(null), eq(0.0));
        ArgumentCaptor<Long> sleeps = ArgumentCaptor.forClass(Long.class);
        verify(fibonacciSleeper, times(3)).sleepMillis(sleeps.capture());
        assertThat(sleeps.getAllValues()).containsExactly(1000L, 1000L, 2000L);
    }

    @Test
    void exhaustsAfterFourTimeouts() throws Exception {
        when(llmClient.chat(any(), any(), any()))
                .thenThrow(new RuntimeException(new TimeoutException("t1")))
                .thenThrow(new RuntimeException(new TimeoutException("t2")))
                .thenThrow(new RuntimeException(new TimeoutException("t3")))
                .thenThrow(new RuntimeException(new TimeoutException("t4")));

        SystemTreePlanServiceImpl svc = new SystemTreePlanServiceImpl(llmClient, fibonacciSleeper);

        assertThatThrownBy(() -> svc.generateUpdatePlanJson("p"))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.LLM_RETRIES_EXHAUSTED));

        verify(llmClient, times(4)).chat(any(), any(), any());
        ArgumentCaptor<Long> sleeps = ArgumentCaptor.forClass(Long.class);
        verify(fibonacciSleeper, times(3)).sleepMillis(sleeps.capture());
        assertThat(sleeps.getAllValues()).containsExactly(1000L, 1000L, 2000L);
    }
}
