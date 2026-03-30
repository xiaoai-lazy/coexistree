package io.github.xiaoailazy.coexistree.document.controller;

import io.github.xiaoailazy.coexistree.shared.api.ApiResponse;
import io.github.xiaoailazy.coexistree.shared.integration.AbstractIntegrationTest;
import io.github.xiaoailazy.coexistree.document.dto.DocumentResponse;
import io.github.xiaoailazy.coexistree.system.dto.CreateSystemRequest;
import io.github.xiaoailazy.coexistree.system.dto.SystemResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class DocumentControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    private static final String DOCUMENTS_URL = "/api/v1/documents";
    private static final String SYSTEMS_URL = "/api/v1/systems";

    private Long systemId;

    @BeforeEach
    void setUp() {
        // Create a system with unique code for each test
        String uniqueCode = "DOC-" + UUID.randomUUID().toString().substring(0, 8);
        ResponseEntity<ApiResponse<SystemResponse>> response = restTemplate.exchange(
                SYSTEMS_URL,
                HttpMethod.POST,
                new HttpEntity<>(new CreateSystemRequest(uniqueCode, "Document Test System", "For document tests")),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isTrue();
        systemId = response.getBody().data().id();
    }

    @Test
    void shouldUploadMarkdownDocument() {
        // Given
        String markdownContent = "# Test Document\n\nThis is a test markdown file.";
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("systemId", systemId);
        body.add("file", createMultipartFile("test.md", markdownContent));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        // When
        ResponseEntity<ApiResponse<DocumentResponse>> response = restTemplate.exchange(
                DOCUMENTS_URL + "/upload",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                new ParameterizedTypeReference<>() {}
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isTrue();
        assertThat(response.getBody().data().systemId()).isEqualTo(systemId);
        assertThat(response.getBody().data().originalFileName()).isEqualTo("test.md");
        assertThat(response.getBody().data().parseStatus()).isEqualTo("PENDING");
    }

    @Test
    void shouldRejectNonMarkdownFiles() {
        // Given
        String textContent = "This is a text file, not markdown.";
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("systemId", systemId);
        body.add("file", createMultipartFile("test.txt", textContent));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        // When
        ResponseEntity<ApiResponse<DocumentResponse>> response = restTemplate.exchange(
                DOCUMENTS_URL + "/upload",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                new ParameterizedTypeReference<>() {}
        );

        // Then - Invalid file type returns 400 Bad Request
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isFalse();
    }

    @Test
    void shouldGetDocumentById() {
        // Given - Upload a document first
        MultiValueMap<String, Object> uploadBody = new LinkedMultiValueMap<>();
        uploadBody.add("systemId", systemId);
        uploadBody.add("file", createMultipartFile("retrieve.md", "# Retrieve Test"));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        ResponseEntity<ApiResponse<DocumentResponse>> uploadResponse = restTemplate.exchange(
                DOCUMENTS_URL + "/upload",
                HttpMethod.POST,
                new HttpEntity<>(uploadBody, headers),
                new ParameterizedTypeReference<>() {}
        );
        Long documentId = uploadResponse.getBody().data().id();

        // When
        ResponseEntity<ApiResponse<DocumentResponse>> getResponse = restTemplate.exchange(
                DOCUMENTS_URL + "/" + documentId,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
        );

        // Then
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getBody()).isNotNull();
        assertThat(getResponse.getBody().data().id()).isEqualTo(documentId);
        assertThat(getResponse.getBody().data().originalFileName()).isEqualTo("retrieve.md");
    }

    @Test
    void shouldListDocumentsBySystem() {
        // Given - Upload multiple documents
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        for (int i = 1; i <= 3; i++) {
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("systemId", systemId);
            body.add("file", createMultipartFile("doc" + i + ".md", "# Doc " + i));

            restTemplate.exchange(
                    DOCUMENTS_URL + "/upload",
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    new ParameterizedTypeReference<ApiResponse<DocumentResponse>>() {}
            );
        }

        // When
        ResponseEntity<ApiResponse<List<DocumentResponse>>> listResponse = restTemplate.exchange(
                DOCUMENTS_URL + "?systemId=" + systemId,
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
    void shouldDeleteDocument() {
        // Given - Upload a document
        MultiValueMap<String, Object> uploadBody = new LinkedMultiValueMap<>();
        uploadBody.add("systemId", systemId);
        uploadBody.add("file", createMultipartFile("to-delete.md", "# To Delete"));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        ResponseEntity<ApiResponse<DocumentResponse>> uploadResponse = restTemplate.exchange(
                DOCUMENTS_URL + "/upload",
                HttpMethod.POST,
                new HttpEntity<>(uploadBody, headers),
                new ParameterizedTypeReference<>() {}
        );
        Long documentId = uploadResponse.getBody().data().id();

        // When - Delete
        ResponseEntity<ApiResponse<Void>> deleteResponse = restTemplate.exchange(
                DOCUMENTS_URL + "/" + documentId,
                HttpMethod.DELETE,
                null,
                new ParameterizedTypeReference<>() {}
        );

        // Then
        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Verify deletion
        ResponseEntity<ApiResponse<DocumentResponse>> getResponse = restTemplate.exchange(
                DOCUMENTS_URL + "/" + documentId,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
        );
        assertThat(getResponse.getBody().success()).isFalse();
    }

    @Test
    void shouldReturnErrorForNonExistentDocument() {
        // When
        ResponseEntity<ApiResponse<DocumentResponse>> response = restTemplate.exchange(
                DOCUMENTS_URL + "/999999",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
        );

        // Then - Not found returns 400 Bad Request
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isFalse();
    }

    @Test
    void shouldProcessDocumentAsynchronously() {
        // Given - 上传一个包含多个章节的大文档，确保处理需要一定时间
        StringBuilder content = new StringBuilder("# Main Title\n\n");
        for (int i = 1; i <= 20; i++) {
            content.append("## Section ").append(i).append("\n\n");
            content.append("This is the content for section ").append(i).append(". ");
            content.append("It contains enough text to make the document substantial.\n\n");
        }

        MultiValueMap<String, Object> uploadBody = new LinkedMultiValueMap<>();
        uploadBody.add("systemId", systemId);
        uploadBody.add("file", createMultipartFile("async-test.md", content.toString()));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        // When - 上传文档
        ResponseEntity<ApiResponse<DocumentResponse>> uploadResponse = restTemplate.exchange(
                DOCUMENTS_URL + "/upload",
                HttpMethod.POST,
                new HttpEntity<>(uploadBody, headers),
                new ParameterizedTypeReference<>() {}
        );

        // Then - 立即返回 PENDING 状态
        assertThat(uploadResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(uploadResponse.getBody()).isNotNull();
        assertThat(uploadResponse.getBody().success()).isTrue();

        Long documentId = uploadResponse.getBody().data().id();
        assertThat(uploadResponse.getBody().data().parseStatus()).isEqualTo("PENDING");
        System.out.println("[TEST] Document uploaded, status: PENDING, id: " + documentId);

        // 轮询等待异步处理完成
        await().atMost(30, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .until(() -> {
                    ResponseEntity<ApiResponse<DocumentResponse>> getResponse = restTemplate.exchange(
                            DOCUMENTS_URL + "/" + documentId,
                            HttpMethod.GET,
                            null,
                            new ParameterizedTypeReference<>() {}
                    );
                    String status = getResponse.getBody().data().parseStatus();
                    System.out.println("[TEST] Polling status: " + status);
                    return "SUCCESS".equals(status) || "FAILED".equals(status);
                });

        // 验证最终状态是 SUCCESS
        ResponseEntity<ApiResponse<DocumentResponse>> finalResponse = restTemplate.exchange(
                DOCUMENTS_URL + "/" + documentId,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
        );

        DocumentResponse finalDoc = finalResponse.getBody().data();
        System.out.println("[TEST] Final status: " + finalDoc.parseStatus());
        System.out.println("[TEST] Parse error: " + finalDoc.parseError());

        assertThat(finalDoc.parseStatus()).isEqualTo("SUCCESS");
        System.out.println("[TEST] Async processing completed successfully!");
    }

    private ByteArrayResource createMultipartFile(String filename, String content) {
        return new ByteArrayResource(content.getBytes()) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
    }
}
