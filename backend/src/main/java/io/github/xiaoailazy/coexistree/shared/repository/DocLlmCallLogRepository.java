package io.github.xiaoailazy.coexistree.shared.repository;

import io.github.xiaoailazy.coexistree.shared.entity.DocLlmCallLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocLlmCallLogRepository extends JpaRepository<DocLlmCallLogEntity, Long> {

    List<DocLlmCallLogEntity> findByProcessLogIdOrderByCreatedAtAsc(Long processLogId);
}
