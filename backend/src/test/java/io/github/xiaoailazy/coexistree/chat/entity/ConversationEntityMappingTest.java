package io.github.xiaoailazy.coexistree.chat.entity;

import io.github.xiaoailazy.coexistree.chat.repository.ConversationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class ConversationEntityMappingTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ConversationRepository conversationRepository;

    @Test
    void testVersionFieldForOptimisticLocking() {
        ConversationEntity conversation = new ConversationEntity();
        conversation.setConversationId(UUID.randomUUID().toString());
        conversation.setSystemId(1L);
        conversation.setTitle("Test Conversation");
        conversation.setCreatedAt(LocalDateTime.now());
        conversation.setUpdatedAt(LocalDateTime.now());

        ConversationEntity saved = conversationRepository.save(conversation);
        entityManager.flush();
        entityManager.clear();

        assertThat(saved.getVersion()).isNotNull();
        assertThat(saved.getVersion()).isEqualTo(0L);

        ConversationEntity loaded = conversationRepository.findById(saved.getId()).orElseThrow();
        loaded.setTitle("Updated Title");
        ConversationEntity updated = conversationRepository.save(loaded);
        entityManager.flush();

        assertThat(updated.getVersion()).isEqualTo(1L);
    }

    @Test
    void testOptimisticLockingConflict() {
        ConversationEntity conversation = new ConversationEntity();
        conversation.setConversationId(UUID.randomUUID().toString());
        conversation.setSystemId(1L);
        conversation.setTitle("Test Conversation");
        conversation.setCreatedAt(LocalDateTime.now());
        conversation.setUpdatedAt(LocalDateTime.now());

        ConversationEntity saved = conversationRepository.save(conversation);
        entityManager.flush();
        entityManager.clear();

        ConversationEntity stale = conversationRepository.findById(saved.getId()).orElseThrow();
        entityManager.flush();
        entityManager.clear();

        stale.setTitle("Stale Update");
        stale.setVersion(0L);

        ConversationEntity loaded = conversationRepository.findById(saved.getId()).orElseThrow();
        assertThat(loaded.getVersion()).isNotNull();
        assertThat(loaded.getTitle()).isEqualTo("Test Conversation");
    }

    @Test
    void testConversationIdUniqueConstraint() {
        String conversationId = UUID.randomUUID().toString();

        ConversationEntity conversation1 = new ConversationEntity();
        conversation1.setConversationId(conversationId);
        conversation1.setSystemId(1L);
        conversation1.setTitle("Conversation 1");
        conversation1.setCreatedAt(LocalDateTime.now());
        conversation1.setUpdatedAt(LocalDateTime.now());

        ConversationEntity conversation2 = new ConversationEntity();
        conversation2.setConversationId(conversationId);
        conversation2.setSystemId(1L);
        conversation2.setTitle("Conversation 2");
        conversation2.setCreatedAt(LocalDateTime.now());
        conversation2.setUpdatedAt(LocalDateTime.now());

        conversationRepository.save(conversation1);
        entityManager.flush();

        assertThatThrownBy(() -> {
            conversationRepository.save(conversation2);
            entityManager.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void testEntityMapping() {
        ConversationEntity conversation = new ConversationEntity();
        conversation.setConversationId(UUID.randomUUID().toString());
        conversation.setSystemId(1L);
        conversation.setTitle("Test Conversation");
        conversation.setCreatedAt(LocalDateTime.now());
        conversation.setUpdatedAt(LocalDateTime.now());

        ConversationEntity saved = conversationRepository.save(conversation);
        entityManager.flush();
        entityManager.clear();

        ConversationEntity loaded = conversationRepository.findById(saved.getId()).orElseThrow();

        assertThat(loaded.getConversationId()).isEqualTo(conversation.getConversationId());
        assertThat(loaded.getSystemId()).isEqualTo(1L);
        assertThat(loaded.getTitle()).isEqualTo("Test Conversation");
        assertThat(loaded.getVersion()).isEqualTo(0L);
    }
}
