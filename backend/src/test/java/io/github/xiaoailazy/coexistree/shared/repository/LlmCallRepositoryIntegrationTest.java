package io.github.xiaoailazy.coexistree.shared.repository;

import io.github.xiaoailazy.coexistree.shared.entity.LlmCallEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class LlmCallRepositoryIntegrationTest {

    @Autowired
    private LlmCallRepository repository;

    @Test
    void shouldSaveAndRetrieveLlmCall() {
        LlmCallEntity entity = new LlmCallEntity();
        entity.setScenario("BASELINE_MERGE");
        entity.setUserId(1L);
        entity.setDocumentId(1L);
        entity.setSystemId(1L);
        entity.setModel("gpt-4o");
        entity.setTemperature(0.0);
        entity.setInputTokens(1200L);
        entity.setOutputTokens(800L);
        entity.setReasoningTokens(200L);
        entity.setTotalTokens(2000L);
        entity.setElapsedMs(3500L);
        entity.setSuccess(true);
        entity.setCreatedAt(LocalDateTime.now());

        LlmCallEntity saved = repository.save(entity);
        assertThat(saved.getId()).isNotNull();

        LlmCallEntity retrieved = repository.findById(saved.getId()).orElseThrow();
        assertThat(retrieved.getScenario()).isEqualTo("BASELINE_MERGE");
        assertThat(retrieved.getInputTokens()).isEqualTo(1200L);
        assertThat(retrieved.getReasoningTokens()).isEqualTo(200L);
        assertThat(retrieved.getElapsedMs()).isEqualTo(3500L);
        assertThat(retrieved.isSuccess()).isTrue();
    }

    @Test
    void shouldFindCallsByDocumentId() {
        LlmCallEntity call1 = createCall(1L, 1L, "SCENARIO_A");
        LlmCallEntity call2 = createCall(1L, 1L, "SCENARIO_B");
        createCall(2L, 2L, "SCENARIO_C");

        repository.saveAll(List.of(call1, call2));

        var calls = repository.findByDocumentIdOrderByCreatedAtAsc(1L);
        assertThat(calls).hasSize(2);
    }

    private LlmCallEntity createCall(Long documentId, Long systemId, String scenario) {
        LlmCallEntity e = new LlmCallEntity();
        e.setScenario(scenario);
        e.setDocumentId(documentId);
        e.setSystemId(systemId);
        e.setModel("gpt-4o");
        e.setElapsedMs(1000L);
        e.setSuccess(true);
        e.setCreatedAt(LocalDateTime.now());
        return e;
    }
}
