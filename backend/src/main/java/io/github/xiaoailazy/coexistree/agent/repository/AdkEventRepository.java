package io.github.xiaoailazy.coexistree.agent.repository;

import io.github.xiaoailazy.coexistree.agent.session.AdkEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AdkEventRepository extends JpaRepository<AdkEventEntity, String> {
    List<AdkEventEntity> findBySessionIdOrderByTimestampAsc(String sessionId);
    void deleteBySessionId(String sessionId);
    List<AdkEventEntity> findByAppNameAndUserId(String appName, String userId);
}
