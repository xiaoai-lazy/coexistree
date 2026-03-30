package io.github.xiaoailazy.coexistree.shared.repository;

import io.github.xiaoailazy.coexistree.shared.entity.ProcessLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProcessLogRepository extends JpaRepository<ProcessLogEntity, Long> {

    List<ProcessLogEntity> findByEntityTypeAndEntityId(String entityType, Long entityId);
}
