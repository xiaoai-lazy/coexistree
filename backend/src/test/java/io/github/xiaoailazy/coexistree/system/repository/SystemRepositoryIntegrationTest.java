package io.github.xiaoailazy.coexistree.system.repository;

import io.github.xiaoailazy.coexistree.system.entity.SystemEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class SystemRepositoryIntegrationTest {

    @Autowired
    private SystemRepository systemRepository;

    private SystemEntity createSystem(String code, String name) {
        SystemEntity system = new SystemEntity();
        system.setSystemCode(code);
        system.setSystemName(name);
        system.setDescription("Test description");
        system.setStatus("ACTIVE");
        system.setCreatedAt(LocalDateTime.now());
        system.setUpdatedAt(LocalDateTime.now());
        return system;
    }

    @Test
    void shouldSaveAndFindSystemById() {
        // Given
        SystemEntity system = createSystem("OPS", "Operations System");

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
        SystemEntity system = createSystem("CRM", "CRM System");
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
        SystemEntity system = createSystem("ERP", "ERP System");
        systemRepository.save(system);

        // When & Then
        assertThat(systemRepository.existsBySystemCode("ERP")).isTrue();
        assertThat(systemRepository.existsBySystemCode("NONEXISTENT")).isFalse();
    }

    @Test
    void shouldFindAllSystems() {
        // Given
        systemRepository.save(createSystem("SYS1", "System 1"));
        systemRepository.save(createSystem("SYS2", "System 2"));
        systemRepository.save(createSystem("SYS3", "System 3"));

        // When
        List<SystemEntity> all = systemRepository.findAll();

        // Then
        assertThat(all).hasSize(3);
    }

    @Test
    void shouldUpdateSystem() {
        // Given
        SystemEntity system = createSystem("UPDATE", "Original Name");
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
        SystemEntity system = createSystem("DELETE", "To Delete");
        SystemEntity saved = systemRepository.save(system);

        // When
        systemRepository.deleteById(saved.getId());

        // Then
        Optional<SystemEntity> found = systemRepository.findById(saved.getId());
        assertThat(found).isEmpty();
    }
}
