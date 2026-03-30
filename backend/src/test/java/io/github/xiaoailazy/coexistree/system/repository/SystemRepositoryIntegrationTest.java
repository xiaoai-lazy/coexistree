package io.github.xiaoailazy.coexistree.system.repository;

import io.github.xiaoailazy.coexistree.shared.integration.AbstractRepositoryTest;
import io.github.xiaoailazy.coexistree.shared.integration.TestDataFactory;
import io.github.xiaoailazy.coexistree.system.entity.SystemEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class SystemRepositoryIntegrationTest extends AbstractRepositoryTest {

    @Autowired
    private SystemRepository systemRepository;

    @Test
    void shouldSaveAndFindSystemById() {
        // Given
        SystemEntity system = TestDataFactory.aSystem()
                .withSystemCode("OPS")
                .withSystemName("Operations System")
                .build();

        // When
        SystemEntity saved = systemRepository.save(system);

        // Then
        Optional<SystemEntity> found = systemRepository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getSystemCode()).isEqualTo("OPS");
        assertThat(found.get().getSystemName()).isEqualTo("Operations System");
    }

    @Test
    void shouldFindBySystemCode() {
        // Given
        SystemEntity system = TestDataFactory.aSystem()
                .withSystemCode("CRM")
                .withSystemName("CRM System")
                .build();
        systemRepository.save(system);

        // When
        Optional<SystemEntity> found = systemRepository.findBySystemCode("CRM");

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getSystemName()).isEqualTo("CRM System");
    }

    @Test
    void shouldReturnEmptyWhenSystemCodeNotFound() {
        // When
        Optional<SystemEntity> found = systemRepository.findBySystemCode("NONEXISTENT");

        // Then
        assertThat(found).isEmpty();
    }

    @Test
    void shouldCheckIfSystemCodeExists() {
        // Given
        SystemEntity system = TestDataFactory.aSystem()
                .withSystemCode("ERP")
                .build();
        systemRepository.save(system);

        // When & Then
        assertThat(systemRepository.existsBySystemCode("ERP")).isTrue();
        assertThat(systemRepository.existsBySystemCode("NONEXISTENT")).isFalse();
    }

    @Test
    void shouldFindAllSystems() {
        // Given
        systemRepository.save(TestDataFactory.aSystem().withSystemCode("SYS1").withSystemName("System 1").build());
        systemRepository.save(TestDataFactory.aSystem().withSystemCode("SYS2").withSystemName("System 2").build());
        systemRepository.save(TestDataFactory.aSystem().withSystemCode("SYS3").withSystemName("System 3").build());

        // When
        List<SystemEntity> all = systemRepository.findAll();

        // Then
        assertThat(all).hasSize(3);
    }

    @Test
    void shouldUpdateSystem() {
        // Given
        SystemEntity system = TestDataFactory.aSystem()
                .withSystemCode("UPDATE")
                .withSystemName("Original Name")
                .build();
        SystemEntity saved = systemRepository.save(system);

        // When
        saved.setSystemName("Updated Name");
        systemRepository.save(saved);

        // Then
        Optional<SystemEntity> found = systemRepository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getSystemName()).isEqualTo("Updated Name");
    }

    @Test
    void shouldDeleteSystem() {
        // Given
        SystemEntity system = TestDataFactory.aSystem()
                .withSystemCode("DELETE")
                .build();
        SystemEntity saved = systemRepository.save(system);

        // When
        systemRepository.deleteById(saved.getId());

        // Then
        Optional<SystemEntity> found = systemRepository.findById(saved.getId());
        assertThat(found).isEmpty();
    }
}
