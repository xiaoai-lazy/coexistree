package io.github.xiaoailazy.coexistree.document.repository;

import io.github.xiaoailazy.coexistree.shared.integration.AbstractRepositoryTest;
import io.github.xiaoailazy.coexistree.shared.integration.TestDataFactory;
import io.github.xiaoailazy.coexistree.document.entity.DocumentEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class DocumentRepositoryIntegrationTest extends AbstractRepositoryTest {

    @Autowired
    private DocumentRepository documentRepository;

    @Test
    void shouldSaveAndFindDocumentById() {
        // Given
        DocumentEntity doc = TestDataFactory.aDocument()
                .withDocName("test.md")
                .withSystemId(1L)
                .withParseStatus("SUCCESS")
                .build();

        // When
        DocumentEntity saved = documentRepository.save(doc);

        // Then
        DocumentEntity found = documentRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getDocName()).isEqualTo("test.md");
        assertThat(found.getSystemId()).isEqualTo(1L);
    }

    @Test
    void shouldFindBySystemId() {
        // Given
        documentRepository.save(TestDataFactory.aDocument()
                .withSystemId(1L)
                .withDocName("doc1.md")
                .build());
        documentRepository.save(TestDataFactory.aDocument()
                .withSystemId(1L)
                .withDocName("doc2.md")
                .build());
        documentRepository.save(TestDataFactory.aDocument()
                .withSystemId(2L)
                .withDocName("other.md")
                .build());

        // When
        List<DocumentEntity> docs = documentRepository.findBySystemId(1L);

        // Then
        assertThat(docs).hasSize(2);
        assertThat(docs).extracting(DocumentEntity::getDocName)
                .containsExactlyInAnyOrder("doc1.md", "doc2.md");
    }

    @Test
    void shouldCountBySystemId() {
        // Given
        documentRepository.save(TestDataFactory.aDocument().withSystemId(1L).withDocName("a.md").build());
        documentRepository.save(TestDataFactory.aDocument().withSystemId(1L).withDocName("b.md").build());
        documentRepository.save(TestDataFactory.aDocument().withSystemId(1L).withDocName("c.md").build());
        documentRepository.save(TestDataFactory.aDocument().withSystemId(2L).withDocName("other.md").build());

        // When
        long count1 = documentRepository.countBySystemId(1L);
        long count2 = documentRepository.countBySystemId(2L);
        long count99 = documentRepository.countBySystemId(99L);

        // Then
        assertThat(count1).isEqualTo(3);
        assertThat(count2).isEqualTo(1);
        assertThat(count99).isZero();
    }

    @Test
    void shouldReturnEmptyListWhenNoDocumentsForSystem() {
        // When
        List<DocumentEntity> docs = documentRepository.findBySystemId(999L);

        // Then
        assertThat(docs).isEmpty();
    }

    @Test
    void shouldUpdateDocumentStatus() {
        // Given
        DocumentEntity doc = TestDataFactory.aDocument()
                .withParseStatus("PENDING")
                .build();
        DocumentEntity saved = documentRepository.save(doc);

        // When
        saved.setParseStatus("SUCCESS");
        documentRepository.save(saved);

        // Then
        DocumentEntity updated = documentRepository.findById(saved.getId()).orElseThrow();
        assertThat(updated.getParseStatus()).isEqualTo("SUCCESS");
    }

    @Test
    void shouldDeleteDocument() {
        // Given
        DocumentEntity doc = TestDataFactory.aDocument().build();
        DocumentEntity saved = documentRepository.save(doc);

        // When
        documentRepository.deleteById(saved.getId());

        // Then
        assertThat(documentRepository.findById(saved.getId())).isEmpty();
    }
}
