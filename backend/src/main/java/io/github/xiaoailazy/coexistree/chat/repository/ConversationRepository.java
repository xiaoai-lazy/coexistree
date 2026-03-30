package io.github.xiaoailazy.coexistree.chat.repository;

import io.github.xiaoailazy.coexistree.chat.entity.ConversationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConversationRepository extends JpaRepository<ConversationEntity, Long> {
    Optional<ConversationEntity> findByConversationId(String conversationId);
    List<ConversationEntity> findAllByOrderByUpdatedAtDesc();
}
