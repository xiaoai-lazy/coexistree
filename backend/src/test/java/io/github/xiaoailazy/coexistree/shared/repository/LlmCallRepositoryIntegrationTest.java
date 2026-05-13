package io.github.xiaoailazy.coexistree.shared.repository;

import io.github.xiaoailazy.coexistree.shared.entity.DocLlmCallLogEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class DocLlmCallLogRepositoryIntegrationTest {

    @Autowired
    private DocLlmCallLogRepository repository;

    @Test
    void shouldSaveAndRetrieveLlmCallLog() {
        DocLlmCallLogEntity entity = new DocLlmCallLogEntity();
        entity.setProcessLogId(1L);
        entity.setModel("gpt-4o");
        entity.setTemperature(0.0);
        entity.setInputTokens(1200L);
        entity.setOutputTokens(800L);
        entity.setReasoningTokens(200L);
        entity.setTotalTokens(2000L);
        entity.setElapsedMs(3500L);
        entity.setSuccess(true);
        entity.setCreatedAt(LocalDateTime.now());

        DocLlmCallLogEntity saved = repository.save(entity);
        assertThat(saved.getId()).isNotNull();

        DocLlmCallLogEntity retrieved = repository.findById(saved.getId()).orElseThrow();
        assertThat(retrieved.getProcessLogId()).isEqualTo(1L);
        assertThat(retrieved.getInputTokens()).isEqualTo(1200L);
        assertThat(retrieved.getReasoningTokens()).isEqualTo(200L);
        assertThat(retrieved.getElapsedMs()).isEqualTo(3500L);
        assertThat(retrieved.isSuccess()).isTrue();
    }

    @Test
    void shouldFindCallsByProcessLogId() {
        DocLlmCallLogEntity call1 = createCall(1L, "gpt-4o");
        DocLlmCallLogEntity call2 = createCall(1L, "gpt-4o-mini");
        createCall(2L);

        repository.saveAll(List.of(call1, call2));

        var calls = repository.findByProcessLogIdOrderByCreatedAtAsc(1L);
        assertThat(calls).hasSize(2);
    }

    private DocLlmCallLogEntity createCall(Long processLogId) {
        return createCall(processLogId, "gpt-4o");
    }

    private DocLlmCallLogEntity createCall(Long processLogId, String model) {
        DocLlmCallLogEntity e = new DocLlmCallLogEntity();
        e.setProcessLogId(processLogId);
        e.setModel(model);
        e.setElapsedMs(1000L);
        e.setSuccess(true);
        e.setCreatedAt(LocalDateTime.now());
        return e;
    }
}
