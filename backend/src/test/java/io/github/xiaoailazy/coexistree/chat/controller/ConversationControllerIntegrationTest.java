package io.github.xiaoailazy.coexistree.chat.controller;

import io.github.xiaoailazy.coexistree.shared.api.ApiResponse;
import io.github.xiaoailazy.coexistree.shared.integration.AbstractIntegrationTest;
import io.github.xiaoailazy.coexistree.chat.dto.ConversationResponse;
import io.github.xiaoailazy.coexistree.chat.dto.CreateConversationRequest;
import io.github.xiaoailazy.coexistree.chat.dto.MessageResponse;
import io.github.xiaoailazy.coexistree.system.dto.CreateSystemRequest;
import io.github.xiaoailazy.coexistree.system.dto.SystemResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    private static final String CONVERSATIONS_URL = "/api/v1/conversations";
    private static final String SYSTEMS_URL = "/api/v1/systems";

    private Long systemId;

    @BeforeEach
    void setUp() {
        // Create a system with unique code for each test
        String uniqueCode = "CONV-" + UUID.randomUUID().toString().substring(0, 8);
        ResponseEntity<ApiResponse<SystemResponse>> response = restTemplate.exchange(
                SYSTEMS_URL,
                HttpMethod.POST,
                new HttpEntity<>(new CreateSystemRequest(uniqueCode, "Conversation Test System", "For conversation tests")),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isTrue();
        systemId = response.getBody().data().id();
    }

    @Test
    void shouldCreateConversation() {
        // Given
        CreateConversationRequest request = new CreateConversationRequest(systemId, "Test Conversation");

        // When
        ResponseEntity<ApiResponse<ConversationResponse>> response = restTemplate.exchange(
                CONVERSATIONS_URL,
                HttpMethod.POST,
                new HttpEntity<>(request),
                new ParameterizedTypeReference<>() {}
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isTrue();
        assertThat(response.getBody().data().systemId()).isEqualTo(systemId);
        assertThat(response.getBody().data().title()).isEqualTo("Test Conversation");
        assertThat(response.getBody().data().conversationId()).isNotNull();
    }

    @Test
    void shouldCreateConversationWithoutTitle() {
        // Given
        CreateConversationRequest request = new CreateConversationRequest(systemId, null);

        // When
        ResponseEntity<ApiResponse<ConversationResponse>> response = restTemplate.exchange(
                CONVERSATIONS_URL,
                HttpMethod.POST,
                new HttpEntity<>(request),
                new ParameterizedTypeReference<>() {}
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data().title()).isNull();
    }

    @Test
    void shouldGetConversationById() {
        // Given - Create a conversation
        CreateConversationRequest request = new CreateConversationRequest(systemId, "My Conversation");
        ResponseEntity<ApiResponse<ConversationResponse>> createResponse = restTemplate.exchange(
                CONVERSATIONS_URL,
                HttpMethod.POST,
                new HttpEntity<>(request),
                new ParameterizedTypeReference<>() {}
        );
        String conversationId = createResponse.getBody().data().conversationId();

        // When
        ResponseEntity<ApiResponse<ConversationResponse>> getResponse = restTemplate.exchange(
                CONVERSATIONS_URL + "/" + conversationId,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
        );

        // Then
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getBody()).isNotNull();
        assertThat(getResponse.getBody().data().conversationId()).isEqualTo(conversationId);
        assertThat(getResponse.getBody().data().title()).isEqualTo("My Conversation");
    }

    @Test
    void shouldListAllConversations() {
        // Given - Create multiple conversations
        for (int i = 1; i <= 3; i++) {
            restTemplate.exchange(
                    CONVERSATIONS_URL,
                    HttpMethod.POST,
                    new HttpEntity<>(new CreateConversationRequest(systemId, "Conversation " + i)),
                    new ParameterizedTypeReference<ApiResponse<ConversationResponse>>() {}
            );
        }

        // When
        ResponseEntity<ApiResponse<List<ConversationResponse>>> listResponse = restTemplate.exchange(
                CONVERSATIONS_URL,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
        );

        // Then
        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listResponse.getBody()).isNotNull();
        assertThat(listResponse.getBody().data()).hasSizeGreaterThanOrEqualTo(3);
    }

    @Test
    void shouldDeleteConversation() {
        // Given - Create a conversation
        CreateConversationRequest request = new CreateConversationRequest(systemId, "To Delete");
        ResponseEntity<ApiResponse<ConversationResponse>> createResponse = restTemplate.exchange(
                CONVERSATIONS_URL,
                HttpMethod.POST,
                new HttpEntity<>(request),
                new ParameterizedTypeReference<>() {}
        );
        String conversationId = createResponse.getBody().data().conversationId();

        // When - Delete
        ResponseEntity<ApiResponse<Void>> deleteResponse = restTemplate.exchange(
                CONVERSATIONS_URL + "/" + conversationId,
                HttpMethod.DELETE,
                null,
                new ParameterizedTypeReference<>() {}
        );

        // Then
        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Verify deletion - should return error
        ResponseEntity<ApiResponse<ConversationResponse>> getResponse = restTemplate.exchange(
                CONVERSATIONS_URL + "/" + conversationId,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
        );
        assertThat(getResponse.getBody().success()).isFalse();
    }

    @Test
    void shouldGetMessagesForConversation() {
        // Given - Create a conversation
        CreateConversationRequest request = new CreateConversationRequest(systemId, "Message Test");
        ResponseEntity<ApiResponse<ConversationResponse>> createResponse = restTemplate.exchange(
                CONVERSATIONS_URL,
                HttpMethod.POST,
                new HttpEntity<>(request),
                new ParameterizedTypeReference<>() {}
        );
        String conversationId = createResponse.getBody().data().conversationId();

        // When
        ResponseEntity<ApiResponse<List<MessageResponse>>> messagesResponse = restTemplate.exchange(
                CONVERSATIONS_URL + "/" + conversationId + "/messages",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
        );

        // Then
        assertThat(messagesResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(messagesResponse.getBody()).isNotNull();
        // New conversation has no messages
        assertThat(messagesResponse.getBody().data()).isEmpty();
    }

    @Test
    void shouldReturnErrorForNonExistentConversation() {
        // When
        ResponseEntity<ApiResponse<ConversationResponse>> response = restTemplate.exchange(
                CONVERSATIONS_URL + "/nonexistent-id",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
        );

        // Then - Not found returns 400 Bad Request
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isFalse();
    }
}
