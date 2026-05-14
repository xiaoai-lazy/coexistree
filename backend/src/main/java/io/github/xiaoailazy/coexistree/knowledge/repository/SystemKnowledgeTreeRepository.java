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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM SystemKnowledgeTreeEntity t WHERE t.systemId = :systemId AND t.treeStatus = :treeStatus")
    Optional<SystemKnowledgeTreeEntity> findBySystemIdAndTreeStatusWithLock(
            @Param("systemId") Long systemId, @Param("treeStatus") String treeStatus);

    Optional<SystemKnowledgeTreeEntity> findBySystemIdAndTreeStatus(Long systemId, String treeStatus);
}
