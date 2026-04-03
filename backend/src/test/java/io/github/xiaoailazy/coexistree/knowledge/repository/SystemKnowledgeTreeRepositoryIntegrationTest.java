package io.github.xiaoailazy.coexistree.knowledge.repository;

import io.github.xiaoailazy.coexistree.knowledge.entity.SystemKnowledgeTreeEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class SystemKnowledgeTreeRepositoryIntegrationTest {

    @Autowired
    private SystemKnowledgeTreeRepository systemKnowledgeTreeRepository;

    private SystemKnowledgeTreeEntity createTree(Long systemId, int version, String status) {
        SystemKnowledgeTreeEntity tree = new SystemKnowledgeTreeEntity();
        tree.setSystemId(systemId);
        tree.setTreeFilePath("/data/trees/" + systemId + "/tree.json");
        tree.setTreeVersion(version);
        tree.setNodeCount(10);
        tree.setTreeStatus(status);
        tree.setCreatedAt(LocalDateTime.now());
        tree.setUpdatedAt(LocalDateTime.now());
        return tree;
    }

    @Test
    void shouldSaveAndFindById() {
        // Given
        SystemKnowledgeTreeEntity tree = createTree(1L, 1, "ACTIVE");

        // When
        SystemKnowledgeTreeEntity saved = systemKnowledgeTreeRepository.save(tree);

        // Then
        Optional<SystemKnowledgeTreeEntity> found = systemKnowledgeTreeRepository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getSystemId()).isEqualTo(1L);
        assertThat(found.get().getTreeVersion()).isEqualTo(1);
    }

    @Test
    void shouldFindBySystemId() {
        // Given
        SystemKnowledgeTreeEntity tree = createTree(1L, 5, "ACTIVE");
        systemKnowledgeTreeRepository.save(tree);

        // When
        Optional<SystemKnowledgeTreeEntity> found = systemKnowledgeTreeRepository.findBySystemId(1L);

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getTreeVersion()).isEqualTo(5);
    }

    @Test
    void shouldReturnEmptyWhenSystemIdNotFound() {
        // When
        Optional<SystemKnowledgeTreeEntity> found = systemKnowledgeTreeRepository.findBySystemId(999L);

        // Then
        assertThat(found).isEmpty();
    }

    @Test
    void shouldUpdateTreeVersion() {
        // Given
        SystemKnowledgeTreeEntity tree = createTree(1L, 1, "ACTIVE");
        SystemKnowledgeTreeEntity saved = systemKnowledgeTreeRepository.save(tree);

        // When
        saved.setTreeVersion(2);
        saved.setNodeCount(20);
        systemKnowledgeTreeRepository.save(saved);

        // Then
        SystemKnowledgeTreeEntity updated = systemKnowledgeTreeRepository.findBySystemId(1L).orElseThrow();
        assertThat(updated.getTreeVersion()).isEqualTo(2);
        assertThat(updated.getNodeCount()).isEqualTo(20);
    }

    @Test
    void shouldUpdateTreeStatus() {
        // Given
        SystemKnowledgeTreeEntity tree = createTree(1L, 1, "BUILDING");
        systemKnowledgeTreeRepository.save(tree);

        // When
        tree.setTreeStatus("ACTIVE");
        systemKnowledgeTreeRepository.save(tree);

        // Then
        SystemKnowledgeTreeEntity updated = systemKnowledgeTreeRepository.findBySystemId(1L).orElseThrow();
        assertThat(updated.getTreeStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void shouldDeleteTree() {
        // Given
        SystemKnowledgeTreeEntity tree = createTree(1L, 1, "ACTIVE");
        SystemKnowledgeTreeEntity saved = systemKnowledgeTreeRepository.save(tree);

        // When
        systemKnowledgeTreeRepository.deleteById(saved.getId());

        // Then
        assertThat(systemKnowledgeTreeRepository.findBySystemId(1L)).isEmpty();
    }
}
