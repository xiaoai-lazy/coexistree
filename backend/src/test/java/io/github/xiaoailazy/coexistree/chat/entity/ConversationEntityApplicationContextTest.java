package io.github.xiaoailazy.coexistree.chat.entity;

import io.github.xiaoailazy.coexistree.chat.repository.ConversationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Application context test to verify the application starts successfully
 * with the ConversationEntity mappings and database migration.
 * 
 * **Validates: Requirements 8.1**
 * 
 * This test verifies:
 * 1. Application context loads successfully
 * 2. Flyway migration V10 is applied
 * 3. JPA validates entity mappings against database schema
 * 4. ConversationRepository bean is available
 */
@SpringBootTest
@ActiveProfiles("test")
class ConversationEntityApplicationContextTest {

    @Autowired
    private ConversationRepository conversationRepository;

    @Test
    void contextLoads() {
        // When: Application context loads
        // Then: ConversationRepository should be available
        assertThat(conversationRepository).isNotNull();
    }

    @Test
    void conversationRepositoryIsAvailable() {
        // Given: Application has started
        // When: We check the repository
        // Then: It should be properly initialized
        assertThat(conversationRepository).isNotNull();
        
        // And: We should be able to call repository methods
        long count = conversationRepository.count();
        assertThat(count).isGreaterThanOrEqualTo(0);
    }
}
