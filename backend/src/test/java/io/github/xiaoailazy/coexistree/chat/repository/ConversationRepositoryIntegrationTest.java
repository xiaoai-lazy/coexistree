package io.github.xiaoailazy.coexistree.chat.repository;

import io.github.xiaoailazy.coexistree.chat.entity.ConversationEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class ConversationRepositoryIntegrationTest {

    @Autowired
    private ConversationRepository conversationRepository;

    private ConversationEntity createConversation(String conversationId, Long systemId, String title) {
        ConversationEntity conv = new ConversationEntity();
        conv.setConversationId(conversationId);
        conv.setSystemId(systemId);
        conv.setTitle(title);
        conv.setCreatedAt(LocalDateTime.now());
        conv.setUpdatedAt(LocalDateTime.now());
        return conv;
    }

    @Test
    void shouldSaveAndFindConversationById() {
        // Given
        String conversationId = UUID.randomUUID().toString();
        ConversationEntity conv = createConversation(conversationId, 1L, "Test Conversation");

        // When
        ConversationEntity saved = conversationRepository.save(conv);

        // Then
        assertThat(saved.getId()).isNotNull();
    }

    @Test
    void shouldFindByConversationId() {
        // Given
        String conversationId = UUID.randomUUID().toString();
        ConversationEntity conv = createConversation(conversationId, 1L, "My Conversation");
        conversationRepository.save(conv);

        // When
        Optional<ConversationEntity> found = conversationRepository.findByConversationId(conversationId);

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getTitle()).isEqualTo("My Conversation");
    }

    @Test
    void shouldReturnEmptyWhenConversationIdNotFound() {
        // When
        Optional<ConversationEntity> found = conversationRepository.findByConversationId("nonexistent-id");

        // Then
        assertThat(found).isEmpty();
    }

    @Test
    void shouldFindAllOrderedByUpdatedAtDesc() {
        // Given
        LocalDateTime now = LocalDateTime.now();

        ConversationEntity conv1 = createConversation(UUID.randomUUID().toString(), 1L, "Older");
        conv1.setUpdatedAt(now.minusHours(2));
        conv1.setCreatedAt(now.minusHours(2));
        conversationRepository.save(conv1);

        ConversationEntity conv2 = createConversation(UUID.randomUUID().toString(), 1L, "Newest");
        conv2.setUpdatedAt(now);
        conv2.setCreatedAt(now);
        conversationRepository.save(conv2);

        ConversationEntity conv3 = createConversation(UUID.randomUUID().toString(), 1L, "Middle");
        conv3.setUpdatedAt(now.minusHours(1));
        conv3.setCreatedAt(now.minusHours(1));
        conversationRepository.save(conv3);

        // When
        List<ConversationEntity> all = conversationRepository.findAllByOrderByUpdatedAtDesc();

        // Then
        assertThat(all).hasSize(3);
        assertThat(all.get(0).getTitle()).isEqualTo("Newest");
        assertThat(all.get(1).getTitle()).isEqualTo("Middle");
        assertThat(all.get(2).getTitle()).isEqualTo("Older");
    }

    @Test
    void shouldUpdateConversationTitle() {
        // Given
        String conversationId = UUID.randomUUID().toString();
        ConversationEntity conv = createConversation(conversationId, 1L, "Original Title");
        ConversationEntity saved = conversationRepository.save(conv);

        // When
        saved.setTitle("Updated Title");
        conversationRepository.save(saved);

        // Then
        ConversationEntity updated = conversationRepository.findByConversationId(conversationId).orElseThrow();
        assertThat(updated.getTitle()).isEqualTo("Updated Title");
    }

    @Test
    void shouldDeleteConversation() {
        // Given
        String conversationId = UUID.randomUUID().toString();
        ConversationEntity conv = createConversation(conversationId, 1L, "To Delete");
        conversationRepository.save(conv);

        // When
        conversationRepository.delete(conv);

        // Then
        assertThat(conversationRepository.findByConversationId(conversationId)).isEmpty();
    }
}
