package io.github.xiaoailazy.coexistree.shared.repository;

import io.github.xiaoailazy.coexistree.shared.entity.DocProcessLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocProcessLogRepository extends JpaRepository<DocProcessLogEntity, Long> {

    List<DocProcessLogEntity> findByEntityTypeAndEntityId(String entityType, Long entityId);
}
