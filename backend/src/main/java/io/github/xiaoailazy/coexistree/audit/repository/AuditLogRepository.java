package io.github.xiaoailazy.coexistree.audit.repository;

import io.github.xiaoailazy.coexistree.audit.entity.AuditLogEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface AuditLogRepository extends JpaRepository<AuditLogEntity, Long> {

    @Query("SELECT a FROM AuditLogEntity a WHERE " +
           "(:userId IS NULL OR a.userId = :userId) AND " +
           "(:actionType IS NULL OR a.actionType = :actionType) AND " +
           "(:startTime IS NULL OR a.createdAt >= :startTime) AND " +
           "(:endTime IS NULL OR a.createdAt <= :endTime) " +
           "ORDER BY a.createdAt DESC")
    Page<AuditLogEntity> findByFilters(
        @Param("userId") Long userId,
        @Param("actionType") String actionType,
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime,
        Pageable pageable);
}
