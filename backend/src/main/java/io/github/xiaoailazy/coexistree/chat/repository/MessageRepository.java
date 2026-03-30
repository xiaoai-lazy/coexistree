package io.github.xiaoailazy.coexistree.chat.repository;

import io.github.xiaoailazy.coexistree.chat.entity.MessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MessageRepository extends JpaRepository<MessageEntity, Long> {
    List<MessageEntity> findByConversationIdOrderByCreatedAt(String conversationId);
    void deleteByConversationId(String conversationId);
}
