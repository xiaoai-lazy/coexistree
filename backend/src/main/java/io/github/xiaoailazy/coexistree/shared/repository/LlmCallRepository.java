package io.github.xiaoailazy.coexistree.shared.repository;

import io.github.xiaoailazy.coexistree.shared.entity.LlmCallEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LlmCallRepository extends JpaRepository<LlmCallEntity, Long> {

    List<LlmCallEntity> findByDocumentIdOrderByCreatedAtAsc(Long documentId);

    List<LlmCallEntity> findBySystemIdOrderByCreatedAtAsc(Long systemId);
}
