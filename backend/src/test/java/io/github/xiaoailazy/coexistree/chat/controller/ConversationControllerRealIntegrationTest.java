package io.github.xiaoailazy.coexistree.chat.controller;

import io.github.xiaoailazy.coexistree.chat.dto.ConversationResponse;
import io.github.xiaoailazy.coexistree.chat.dto.CreateConversationRequest;
import io.github.xiaoailazy.coexistree.shared.api.ApiResponse;
import io.github.xiaoailazy.coexistree.shared.test.BaseDataIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.jdbc.Sql;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 对话集成测试 - 使用真实服务
 *
 * 继承 BaseDataIntegrationTest 获得预置数据
 */
@Sql(scripts = "/sql/base-test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
@Sql(scripts = "/sql/conversation-test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
@Sql(scripts = "/sql/cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_CLASS)
class ConversationControllerRealIntegrationTest extends BaseDataIntegrationTest {

    private static final String BASE_URL = "/api/v1/conversations";

    @Test
    @DisplayName("查询会话列表 - 使用预置数据")
    void shouldListConversationsUsingExistingData() {
        // Given: conversation-test-data.sql 已创建 3 个会话

        // When
        ResponseEntity<ApiResponse<List<ConversationResponse>>> response = restTemplate.exchange(
                BASE_URL,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().success()).isTrue();
        assertThat(response.getBody().data()).hasSizeGreaterThanOrEqualTo(3);
    }

    @Test
    @DisplayName("创建会话")
    void shouldCreateConversation() {
        // Given: 使用预置的 systemId=1
        CreateConversationRequest request = new CreateConversationRequest(1L, "New Test Conversation");

        // When
        ResponseEntity<ApiResponse<ConversationResponse>> response = restTemplate.exchange(
                BASE_URL,
                HttpMethod.POST,
                new HttpEntity<>(request),
                new ParameterizedTypeReference<>() {}
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().success()).isTrue();
        assertThat(response.getBody().data().title()).isEqualTo("New Test Conversation");
        assertThat(response.getBody().data().systemId()).isEqualTo(1L);

        // Verify conversation ID format (UUID)
        String conversationId = response.getBody().data().conversationId();
        assertThat(UUID.fromString(conversationId)).isNotNull();
    }

    @Test
    @DisplayName("查询指定会话")
    void shouldGetConversationById() {
        // Given: 使用预置的 conversationId
        String conversationId = "test-conv-001";

        // When
        ResponseEntity<ApiResponse<ConversationResponse>> response = restTemplate.exchange(
                BASE_URL + "/" + conversationId,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().success()).isTrue();
        assertThat(response.getBody().data().conversationId()).isEqualTo(conversationId);
        assertThat(response.getBody().data().title()).isEqualTo("Test Conversation 1");
    }

    @Test
    @DisplayName("删除会话")
    void shouldDeleteConversation() {
        // Given: 使用预置的 conversationId
        String conversationId = "test-conv-002";

        // When
        ResponseEntity<ApiResponse<Void>> response = restTemplate.exchange(
                BASE_URL + "/" + conversationId,
                HttpMethod.DELETE,
                null,
                new ParameterizedTypeReference<>() {}
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().success()).isTrue();

        // Verify deleted
        ResponseEntity<ApiResponse<ConversationResponse>> getResponse = restTemplate.exchange(
                BASE_URL + "/" + conversationId,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
        );
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("查询不存在的会话应返回错误")
    void shouldReturnErrorForNonExistentConversation() {
        // When
        ResponseEntity<ApiResponse<ConversationResponse>> response = restTemplate.exchange(
                BASE_URL + "/non-existent-id",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().success()).isFalse();
    }
}
