package io.github.xiaoailazy.coexistree.change.repository;

import io.github.xiaoailazy.coexistree.change.entity.SystemChangeRecordEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SystemChangeRecordRepository extends JpaRepository<SystemChangeRecordEntity, Long> {

    Optional<SystemChangeRecordEntity> findByIdAndSystemId(Long id, Long systemId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from SystemChangeRecordEntity r where r.id = :id")
    Optional<SystemChangeRecordEntity> findByIdForUpdate(@Param("id") Long id);
}
