package io.github.xiaoailazy.coexistree.agent.repository;

import io.github.xiaoailazy.coexistree.agent.session.AdkSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AdkSessionRepository extends JpaRepository<AdkSessionEntity, String> {
    Optional<AdkSessionEntity> findByAppNameAndUserIdAndId(String appName, String userId, String sessionId);
    List<AdkSessionEntity> findByAppNameAndUserId(String appName, String userId);
}
