package io.github.xiaoailazy.coexistree.knowledge.repository;

import io.github.xiaoailazy.coexistree.knowledge.entity.SystemTreeSnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SystemTreeSnapshotRepository extends JpaRepository<SystemTreeSnapshotEntity, Long> {

    /**
     * 查找指定系统的快照（按创建时间倒序）
     */
    List<SystemTreeSnapshotEntity> findBySystemIdOrderByCreatedAtDesc(Long systemId);

    /**
     * 根据快照名称查找
     */
    Optional<SystemTreeSnapshotEntity> findBySnapshotName(String snapshotName);

    /**
     * 查找超过指定日期且未固定的快照
     */
    @Query("SELECT s FROM SystemTreeSnapshotEntity s WHERE s.createdAt < :beforeDate AND s.isPinned = false AND s.status = 'ACTIVE'")
    List<SystemTreeSnapshotEntity> findOlderThanAndNotPinned(@Param("beforeDate") LocalDateTime beforeDate);

    /**
     * 查找指定系统的所有快照（按创建时间倒序，用于获取最新的N个）
     */
    @Query("SELECT s FROM SystemTreeSnapshotEntity s WHERE s.systemId = :systemId AND s.status = 'ACTIVE' ORDER BY s.createdAt DESC")
    List<SystemTreeSnapshotEntity> findAllBySystemIdOrderByCreatedAtDesc(@Param("systemId") Long systemId);
}
