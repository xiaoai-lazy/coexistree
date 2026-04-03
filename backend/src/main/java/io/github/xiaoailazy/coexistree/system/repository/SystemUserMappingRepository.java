package io.github.xiaoailazy.coexistree.system.repository;

import io.github.xiaoailazy.coexistree.system.entity.RelationType;
import io.github.xiaoailazy.coexistree.system.entity.SystemUserMappingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SystemUserMappingRepository extends JpaRepository<SystemUserMappingEntity, Long> {

    List<SystemUserMappingEntity> findByUserId(Long userId);

    List<SystemUserMappingEntity> findBySystemId(Long systemId);

    Optional<SystemUserMappingEntity> findBySystemIdAndUserId(Long systemId, Long userId);

    boolean existsBySystemIdAndUserId(Long systemId, Long userId);

    @Query("SELECT m FROM SystemUserMappingEntity m WHERE m.systemId = :systemId AND m.relationType = :type")
    List<SystemUserMappingEntity> findBySystemIdAndRelationType(
            @Param("systemId") Long systemId,
            @Param("type") RelationType type);

    @Query("SELECT m FROM SystemUserMappingEntity m WHERE m.userId = :userId AND m.relationType IN :types")
    List<SystemUserMappingEntity> findByUserIdAndRelationTypes(
            @Param("userId") Long userId,
            @Param("types") List<RelationType> types);

    void deleteBySystemIdAndUserId(Long systemId, Long userId);

    int countBySystemId(Long systemId);
}
