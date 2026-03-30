package io.github.xiaoailazy.coexistree.knowledge.repository;

import io.github.xiaoailazy.coexistree.knowledge.entity.SystemKnowledgeTreeEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SystemKnowledgeTreeRepository extends JpaRepository<SystemKnowledgeTreeEntity, Long> {

    Optional<SystemKnowledgeTreeEntity> findBySystemId(Long systemId);

    /**
     * 带悲观锁的查询，用于并发控制
     * 防止同时创建系统树的冲突
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM SystemKnowledgeTreeEntity t WHERE t.systemId = :systemId")
    Optional<SystemKnowledgeTreeEntity> findBySystemIdWithLock(@Param("systemId") Long systemId);
}
