package io.github.xiaoailazy.coexistree.shared.aspect;

import io.github.xiaoailazy.coexistree.indexer.llm.LlmClient;
import io.github.xiaoailazy.coexistree.shared.entity.LlmCallEntity;
import io.github.xiaoailazy.coexistree.shared.repository.LlmCallRepository;
import io.github.xiaoailazy.coexistree.shared.util.LlmCallContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LlmCallTrackingAspectTest {

    @Mock
    private LlmCallRepository llmCallRepository;

    @Mock
    private ProceedingJoinPoint joinPoint;

    private LlmCallTrackingAspect aspect;

    @BeforeEach
    void setUp() {
        aspect = new LlmCallTrackingAspect(llmCallRepository);
    }

    @AfterEach
    void tearDown() {
        LlmCallContext.clear();
    }

    @Test
    void shouldTrackSuccessfulCallWithUsage() throws Throwable {
        LlmClient.LlmResponse.Usage usage = new LlmClient.LlmResponse.Usage(
                100L, 200L, 300L, 50L
        );
        LlmClient.LlmResponse response = new LlmClient.LlmResponse(
                "resp_1", "hello", usage
        );

        setupMockCall(response, "test prompt", "gpt-4o", 0.5);

        Object result = aspect.trackLlmCall(joinPoint);

        assertThat(result).isSameAs(response);

        ArgumentCaptor<LlmCallEntity> captor = ArgumentCaptor.forClass(LlmCallEntity.class);
        verify(llmCallRepository).save(captor.capture());

        LlmCallEntity saved = captor.getValue();
        assertThat(saved.getScenario()).isNotNull();
        assertThat(saved.getModel()).isEqualTo("gpt-4o");
        assertThat(saved.getTemperature()).isEqualTo(0.5);
        assertThat(saved.getInputTokens()).isEqualTo(100L);
        assertThat(saved.getOutputTokens()).isEqualTo(200L);
        assertThat(saved.getTotalTokens()).isEqualTo(300L);
        assertThat(saved.getReasoningTokens()).isEqualTo(50L);
        assertThat(saved.isSuccess()).isTrue();
        assertThat(saved.getErrorMessage()).isNull();
        assertThat(saved.getElapsedMs()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void shouldTrackErrorResponse() throws Throwable {
        LlmClient.LlmResponse response = new LlmClient.LlmResponse(
                null, "Error: rate limit exceeded", null
        );

        setupMockCall(response, "test prompt", null, null);

        Object result = aspect.trackLlmCall(joinPoint);

        assertThat(result).isSameAs(response);

        ArgumentCaptor<LlmCallEntity> captor = ArgumentCaptor.forClass(LlmCallEntity.class);
        verify(llmCallRepository).save(captor.capture());

        LlmCallEntity saved = captor.getValue();
        assertThat(saved.isSuccess()).isFalse();
        assertThat(saved.getErrorMessage()).isEqualTo("Error: rate limit exceeded");
    }

    @Test
    void shouldTrackException() throws Throwable {
        RuntimeException expected = new RuntimeException("connection refused");
        setupMockCallToThrow(expected, "test prompt", "gpt-4o", 0.3);

        assertThatThrownBy(() -> aspect.trackLlmCall(joinPoint))
                .isSameAs(expected);

        ArgumentCaptor<LlmCallEntity> captor = ArgumentCaptor.forClass(LlmCallEntity.class);
        verify(llmCallRepository).save(captor.capture());

        LlmCallEntity saved = captor.getValue();
        assertThat(saved.isSuccess()).isFalse();
        assertThat(saved.getErrorMessage()).isEqualTo("connection refused");
        assertThat(saved.getModel()).isEqualTo("gpt-4o");
    }

    @Test
    void shouldUseContextScenarioWhenSet() throws Throwable {
        LlmClient.LlmResponse response = new LlmClient.LlmResponse("r1", "ok", null);
        setupMockCall(response, "prompt", "gpt-4o", 0.0);

        LlmCallContext.set("EXPLICIT_SCENARIO", 42L, 99L, 7L);

        aspect.trackLlmCall(joinPoint);

        ArgumentCaptor<LlmCallEntity> captor = ArgumentCaptor.forClass(LlmCallEntity.class);
        verify(llmCallRepository).save(captor.capture());

        LlmCallEntity saved = captor.getValue();
        assertThat(saved.getScenario()).isEqualTo("EXPLICIT_SCENARIO");
        assertThat(saved.getDocumentId()).isEqualTo(42L);
        assertThat(saved.getSystemId()).isEqualTo(99L);
        assertThat(saved.getUserId()).isEqualTo(7L);
    }

    @Test
    void shouldFallbackToStackDetectionWhenNoContext() throws Throwable {
        LlmClient.LlmResponse response = new LlmClient.LlmResponse("r1", "ok", null);
        // No LlmCallContext set - should fallback to stack detection
        setupMockCall(response, "prompt", "gpt-4o", 0.0);

        aspect.trackLlmCall(joinPoint);

        ArgumentCaptor<LlmCallEntity> captor = ArgumentCaptor.forClass(LlmCallEntity.class);
        verify(llmCallRepository).save(captor.capture());

        LlmCallEntity saved = captor.getValue();
        // Should not be UNKNOWN since StackWalker will detect this test class
        assertThat(saved.getScenario()).isNotNull();
        assertThat(saved.getScenario()).isNotEqualTo("UNKNOWN");
    }

    @Test
    void shouldGracefullyHandleRepositoryFailure() throws Throwable {
        LlmClient.LlmResponse response = new LlmClient.LlmResponse("r1", "ok", null);
        setupMockCall(response, "prompt", "gpt-4o", 0.0);

        doThrow(new RuntimeException("db down")).when(llmCallRepository).save(any());

        // Should NOT throw - aspect swallows repository errors
        Object result = aspect.trackLlmCall(joinPoint);
        assertThat(result).isSameAs(response);
    }

    private void setupMockCall(LlmClient.LlmResponse response, String prompt, String model, Double temperature)
            throws Throwable {
        when(joinPoint.proceed()).thenReturn(response);
        when(joinPoint.getArgs()).thenReturn(new Object[]{prompt, model, temperature});
    }

    private void setupMockCallToThrow(Throwable t, String prompt, String model, Double temperature)
            throws Throwable {
        when(joinPoint.proceed()).thenThrow(t);
        when(joinPoint.getArgs()).thenReturn(new Object[]{prompt, model, temperature});
    }
}
