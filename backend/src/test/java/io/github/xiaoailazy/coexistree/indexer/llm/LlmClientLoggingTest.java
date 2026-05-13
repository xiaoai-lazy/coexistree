package io.github.xiaoailazy.coexistree.indexer.llm;

import io.github.xiaoailazy.coexistree.shared.entity.DocLlmCallLogEntity;
import io.github.xiaoailazy.coexistree.shared.repository.DocLlmCallLogRepository;
import io.github.xiaoailazy.coexistree.shared.util.LlmCallContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Tests that LlmClient persists call logs when LlmCallContext is set.
 */
@DataJpaTest
@ActiveProfiles("test")
class LlmClientLoggingTest {

    @Autowired
    private DocLlmCallLogRepository repository;

    @MockBean
    private io.github.xiaoailazy.coexistree.config.LlmProperties llmProperties;

    private LlmClient llmClient;

    @BeforeEach
    void setUp() {
        when(llmProperties.getBaseUrl()).thenReturn("https://api.test.com");
        when(llmProperties.getApiKey()).thenReturn("test-key");
        when(llmProperties.getModel()).thenReturn("gpt-4o-mini");
        when(llmProperties.getTemperature()).thenReturn(0.0);
        when(llmProperties.getTimeout()).thenReturn(30000);

        llmClient = new LlmClient(llmProperties, repository);
    }

    @AfterEach
    void tearDown() {
        LlmCallContext.clear();
        repository.deleteAll();
    }

    @Test
    void shouldLogLlmCallWhenContextIsSet() {
        // Use Mockito spy to wrap the real repository so the LlmClient's internal save
        // goes through the real repo (DataJpaTest provides a real one via @Autowired),
        // but the mock bean for LlmProperties prevents actual HTTP calls.
        // However, the LlmClient builds its own OpenAiChatModel which will try to call
        // the real API. We test the logging path by catching the expected failure.

        LlmCallContext.set("TEST_SCENARIO", 1L, null, null, null);

        // The call will fail because our baseUrl is fake, but the error path
        // should still record the log entry.
        LlmClient.LlmResponse response = llmClient.chat("test prompt", null, 0.0);

        // Verify a log entry was created
        var logs = repository.findAll();
        assertThat(logs).isNotEmpty();

        DocLlmCallLogEntity logged = logs.get(0);
        assertThat(logged.getProcessLogId()).isEqualTo(1L);
        assertThat(logged.getModel()).isEqualTo("gpt-4o-mini");
        assertThat(logged.getElapsedMs()).isGreaterThan(0);
    }

    @Test
    void shouldNotLogWhenContextIsNotSet() {
        // No LlmCallContext.set() call
        repository.deleteAll();

        llmClient.chat("test prompt", null, 0.0);

        assertThat(repository.findAll()).isEmpty();
    }
}
