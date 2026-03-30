package io.github.xiaoailazy.coexistree.knowledge.repository;

import io.github.xiaoailazy.coexistree.shared.integration.AbstractRepositoryTest;
import io.github.xiaoailazy.coexistree.shared.integration.TestDataFactory;
import io.github.xiaoailazy.coexistree.knowledge.entity.SystemKnowledgeTreeEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class SystemKnowledgeTreeRepositoryIntegrationTest extends AbstractRepositoryTest {

    @Autowired
    private SystemKnowledgeTreeRepository systemKnowledgeTreeRepository;

    @Test
    void shouldSaveAndFindById() {
        // Given
        SystemKnowledgeTreeEntity tree = TestDataFactory.aSystemKnowledgeTree()
                .withSystemId(1L)
                .withTreeVersion(1)
                .withNodeCount(10)
                .withTreeStatus("ACTIVE")
                .build();

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
        SystemKnowledgeTreeEntity tree = TestDataFactory.aSystemKnowledgeTree()
                .withSystemId(1L)
                .withTreeVersion(5)
                .withTreeStatus("ACTIVE")
                .build();
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
        SystemKnowledgeTreeEntity tree = TestDataFactory.aSystemKnowledgeTree()
                .withSystemId(1L)
                .withTreeVersion(1)
                .build();
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
        SystemKnowledgeTreeEntity tree = TestDataFactory.aSystemKnowledgeTree()
                .withSystemId(1L)
                .withTreeStatus("BUILDING")
                .build();
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
        SystemKnowledgeTreeEntity tree = TestDataFactory.aSystemKnowledgeTree()
                .withSystemId(1L)
                .build();
        SystemKnowledgeTreeEntity saved = systemKnowledgeTreeRepository.save(tree);

        // When
        systemKnowledgeTreeRepository.deleteById(saved.getId());

        // Then
        assertThat(systemKnowledgeTreeRepository.findBySystemId(1L)).isEmpty();
    }
}
