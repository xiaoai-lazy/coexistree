package io.github.xiaoailazy.coexistree.system.repository;

import io.github.xiaoailazy.coexistree.system.entity.SystemEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SystemRepository extends JpaRepository<SystemEntity, Long> {
    Optional<SystemEntity> findBySystemCode(String systemCode);
    boolean existsBySystemCode(String systemCode);
}

