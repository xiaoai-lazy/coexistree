package io.github.xiaoailazy.coexistree.document.controller;

import io.github.xiaoailazy.coexistree.document.dto.DocumentResponse;
import io.github.xiaoailazy.coexistree.shared.api.ApiResponse;
import io.github.xiaoailazy.coexistree.shared.test.BaseDataIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 文档集成测试 - 使用真实服务（非 Mock）
 *
 * 继承 BaseDataIntegrationTest 获得预置数据：
 * - systemId=1: 有 2 个文档 (order-api.md, user-guide.md)
 * - systemId=2: 有 1 个文档 (other-system.md)
 */
class DocumentControllerRealIntegrationTest extends BaseDataIntegrationTest {

    private static final String BASE_URL = "/api/v1/documents";

    @Test
    @DisplayName("查询系统文档列表 - 使用预置数据")
    void shouldListDocumentsBySystem() {
        // Given: base-test-data.sql 已为 systemId=1 创建 2 个文档

        // When
        ResponseEntity<ApiResponse<List<DocumentResponse>>> response = restTemplate.exchange(
                BASE_URL + "?systemId=1",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isTrue();

        List<DocumentResponse> documents = response.getBody().data();
        assertThat(documents).hasSize(2);

        // 验证预置数据
        assertThat(documents)
                .extracting(DocumentResponse::originalFileName)
                .containsExactlyInAnyOrder("order-api.md", "user-guide.md");

        // 验证解析状态
        assertThat(documents)
                .extracting(DocumentResponse::parseStatus)
                .contains("SUCCESS", "PROCESSING");
    }

    @Test
    @DisplayName("查询另一个系统的文档")
    void shouldListDocumentsForSystem2() {
        // When
        ResponseEntity<ApiResponse<List<DocumentResponse>>> response = restTemplate.exchange(
                BASE_URL + "?systemId=2",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().data()).hasSize(1);
        assertThat(response.getBody().data().get(0).originalFileName()).isEqualTo("other-system.md");
    }

    @Test
    @DisplayName("查询单个文档")
    void shouldGetDocumentById() {
        // Given: 使用预置的 documentId=1

        // When
        ResponseEntity<ApiResponse<DocumentResponse>> response = restTemplate.exchange(
                BASE_URL + "/1",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().success()).isTrue();
        assertThat(response.getBody().data().id()).isEqualTo(1L);
        assertThat(response.getBody().data().originalFileName()).isEqualTo("order-api.md");
    }

    @Test
    @DisplayName("删除文档")
    void shouldDeleteDocument() {
        // Given: 使用预置的 documentId=3 (other-system.md)，system2 的文档
        // testuser 是 system2 的 owner，应该可以删除

        // When
        ResponseEntity<ApiResponse<Void>> response = restTemplate.exchange(
                BASE_URL + "/3",
                HttpMethod.DELETE,
                null,
                new ParameterizedTypeReference<>() {}
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().success()).isTrue();

        // 验证已删除
        ResponseEntity<ApiResponse<DocumentResponse>> getResponse = restTemplate.exchange(
                BASE_URL + "/3",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
        );
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("更新文档安全等级")
    void shouldUpdateSecurityLevel() {
        // Given: documentId=1，当前 securityLevel=1

        // When
        ResponseEntity<ApiResponse<Void>> response = restTemplate.exchange(
                BASE_URL + "/1/security-level?securityLevel=2",
                HttpMethod.PUT,
                null,
                new ParameterizedTypeReference<>() {}
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().success()).isTrue();

        // 验证更新
        ResponseEntity<ApiResponse<DocumentResponse>> getResponse = restTemplate.exchange(
                BASE_URL + "/1",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
        );
        assertThat(getResponse.getBody().data().securityLevel()).isEqualTo(2);
    }

    @Test
    @DisplayName("查询不存在的文档应返回错误")
    void shouldReturnErrorForNonExistentDocument() {
        // When
        ResponseEntity<ApiResponse<DocumentResponse>> response = restTemplate.exchange(
                BASE_URL + "/99999",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().success()).isFalse();
    }
}
