package io.github.xiaoailazy.coexistree.document.repository;

import io.github.xiaoailazy.coexistree.document.entity.DocumentEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class DocumentRepositoryIntegrationTest {

    @Autowired
    private DocumentRepository documentRepository;

    private DocumentEntity createDocument(Long systemId, String fileName, String status) {
        DocumentEntity doc = new DocumentEntity();
        doc.setSystemId(systemId);
        doc.setDocName(fileName);
        doc.setOriginalFileName(fileName);
        doc.setFilePath("/test/" + fileName);
        doc.setContentType("text/markdown");
        doc.setParseStatus(status);
        doc.setDocType("MARKDOWN");
        doc.setSecurityLevel(1);
        doc.setCreatedAt(LocalDateTime.now());
        doc.setUpdatedAt(LocalDateTime.now());
        return doc;
    }

    @Test
    void shouldSaveAndFindDocumentById() {
        // Given
        DocumentEntity doc = createDocument(1L, "test.md", "SUCCESS");

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
        documentRepository.save(createDocument(1L, "doc1.md", "SUCCESS"));
        documentRepository.save(createDocument(1L, "doc2.md", "SUCCESS"));
        documentRepository.save(createDocument(2L, "other.md", "SUCCESS"));

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
        documentRepository.save(createDocument(1L, "a.md", "SUCCESS"));
        documentRepository.save(createDocument(1L, "b.md", "SUCCESS"));
        documentRepository.save(createDocument(1L, "c.md", "SUCCESS"));
        documentRepository.save(createDocument(2L, "other.md", "SUCCESS"));

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
        DocumentEntity doc = createDocument(1L, "test.md", "PENDING");
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
        DocumentEntity doc = createDocument(1L, "test.md", "SUCCESS");
        DocumentEntity saved = documentRepository.save(doc);

        // When
        documentRepository.deleteById(saved.getId());

        // Then
        assertThat(documentRepository.findById(saved.getId())).isEmpty();
    }
}
