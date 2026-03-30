package io.github.xiaoailazy.coexistree.document.repository;

import io.github.xiaoailazy.coexistree.document.entity.DocumentTreeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DocumentTreeRepository extends JpaRepository<DocumentTreeEntity, Long> {
    Optional<DocumentTreeEntity> findByDocumentId(Long documentId);
}
