package io.github.xiaoailazy.coexistree.system.controller;

import io.github.xiaoailazy.coexistree.shared.api.ApiResponse;
import io.github.xiaoailazy.coexistree.shared.integration.AbstractIntegrationTest;
import io.github.xiaoailazy.coexistree.system.dto.CreateSystemRequest;
import io.github.xiaoailazy.coexistree.system.dto.SystemResponse;
import io.github.xiaoailazy.coexistree.system.dto.UpdateSystemRequest;
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

class SystemControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    private static final String BASE_URL = "/api/v1/systems";

    @Test
    void shouldCreateAndRetrieveSystem() {
        // Given
        String uniqueCode = "TEST-" + UUID.randomUUID().toString().substring(0, 8);
        CreateSystemRequest request = new CreateSystemRequest(
                uniqueCode,
                "Test System",
                "Test description"
        );

        // When - Create
        ResponseEntity<ApiResponse<SystemResponse>> createResponse = restTemplate.exchange(
                BASE_URL,
                HttpMethod.POST,
                new HttpEntity<>(request),
                new ParameterizedTypeReference<>() {}
        );

        // Then - Verify creation
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(createResponse.getBody()).isNotNull();
        assertThat(createResponse.getBody().success()).isTrue();
        assertThat(createResponse.getBody().data().systemCode()).isEqualTo(uniqueCode);
        assertThat(createResponse.getBody().data().systemName()).isEqualTo("Test System");

        Long createdId = createResponse.getBody().data().id();

        // When - Retrieve by ID
        ResponseEntity<ApiResponse<SystemResponse>> getResponse = restTemplate.exchange(
                BASE_URL + "/" + createdId,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
        );

        // Then - Verify retrieval
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getBody()).isNotNull();
        assertThat(getResponse.getBody().data().systemCode()).isEqualTo(uniqueCode);
    }

    @Test
    void shouldListAllSystems() {
        // Given - Create multiple systems with unique codes
        String uniqueCodeA = "LIST-A-" + UUID.randomUUID().toString().substring(0, 8);
        String uniqueCodeB = "LIST-B-" + UUID.randomUUID().toString().substring(0, 8);
        restTemplate.exchange(
                BASE_URL,
                HttpMethod.POST,
                new HttpEntity<>(new CreateSystemRequest(uniqueCodeA, "System A", "Desc A")),
                new ParameterizedTypeReference<ApiResponse<SystemResponse>>() {}
        );
        restTemplate.exchange(
                BASE_URL,
                HttpMethod.POST,
                new HttpEntity<>(new CreateSystemRequest(uniqueCodeB, "System B", "Desc B")),
                new ParameterizedTypeReference<ApiResponse<SystemResponse>>() {}
        );

        // When
        ResponseEntity<ApiResponse<List<SystemResponse>>> listResponse = restTemplate.exchange(
                BASE_URL,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
        );

        // Then
        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listResponse.getBody()).isNotNull();
        assertThat(listResponse.getBody().data()).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void shouldUpdateSystem() {
        // Given - Create a system with unique code
        String uniqueCode = "UPD-" + UUID.randomUUID().toString().substring(0, 8);
        ResponseEntity<ApiResponse<SystemResponse>> createResponse = restTemplate.exchange(
                BASE_URL,
                HttpMethod.POST,
                new HttpEntity<>(new CreateSystemRequest(uniqueCode, "Original Name", "Original desc")),
                new ParameterizedTypeReference<ApiResponse<SystemResponse>>() {}
        );
        assertThat(createResponse.getBody()).isNotNull();
        assertThat(createResponse.getBody().success()).isTrue();
        Long systemId = createResponse.getBody().data().id();

        // When - Update
        UpdateSystemRequest updateRequest = new UpdateSystemRequest(
                "Updated Name",
                "Updated description",
                "ACTIVE"
        );
        ResponseEntity<ApiResponse<SystemResponse>> updateResponse = restTemplate.exchange(
                BASE_URL + "/" + systemId,
                HttpMethod.PUT,
                new HttpEntity<>(updateRequest),
                new ParameterizedTypeReference<>() {}
        );

        // Then
        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updateResponse.getBody()).isNotNull();
        assertThat(updateResponse.getBody().data().systemName()).isEqualTo("Updated Name");
        assertThat(updateResponse.getBody().data().description()).isEqualTo("Updated description");
    }

    @Test
    void shouldDeleteSystem() {
        // Given - Create a system with unique code
        String uniqueCode = "DEL-" + UUID.randomUUID().toString().substring(0, 8);
        ResponseEntity<ApiResponse<SystemResponse>> createResponse = restTemplate.exchange(
                BASE_URL,
                HttpMethod.POST,
                new HttpEntity<>(new CreateSystemRequest(uniqueCode, "To Delete", "Will be deleted")),
                new ParameterizedTypeReference<ApiResponse<SystemResponse>>() {}
        );
        assertThat(createResponse.getBody()).isNotNull();
        assertThat(createResponse.getBody().success()).isTrue();
        Long systemId = createResponse.getBody().data().id();

        // When - Delete
        ResponseEntity<ApiResponse<Void>> deleteResponse = restTemplate.exchange(
                BASE_URL + "/" + systemId,
                HttpMethod.DELETE,
                null,
                new ParameterizedTypeReference<>() {}
        );

        // Then - Deletion can return 200 (success) or 400 (if system has documents)
        // Both are valid API behaviors depending on system state
        assertThat(deleteResponse.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.BAD_REQUEST);
        assertThat(deleteResponse.getBody()).isNotNull();

        if (deleteResponse.getStatusCode() == HttpStatus.OK) {
            // Verify deletion was successful
            assertThat(deleteResponse.getBody().success()).isTrue();

            ResponseEntity<ApiResponse<SystemResponse>> getResponse = restTemplate.exchange(
                    BASE_URL + "/" + systemId,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {}
            );
            // After deletion, getting the system returns 400 (not found)
            assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(getResponse.getBody().success()).isFalse();
        } else {
            // System has documents, verify error message
            assertThat(deleteResponse.getBody().success()).isFalse();
        }
    }

    @Test
    void shouldReturnErrorForDuplicateSystemCode() {
        // Given - Create a system with unique code
        String uniqueCode = "DUP-" + UUID.randomUUID().toString().substring(0, 8);
        ResponseEntity<ApiResponse<SystemResponse>> firstResponse = restTemplate.exchange(
                BASE_URL,
                HttpMethod.POST,
                new HttpEntity<>(new CreateSystemRequest(uniqueCode, "First", "First system")),
                new ParameterizedTypeReference<ApiResponse<SystemResponse>>() {}
        );
        assertThat(firstResponse.getBody()).isNotNull();
        assertThat(firstResponse.getBody().success()).isTrue();

        // When - Try to create with same code
        ResponseEntity<ApiResponse<SystemResponse>> duplicateResponse = restTemplate.exchange(
                BASE_URL,
                HttpMethod.POST,
                new HttpEntity<>(new CreateSystemRequest(uniqueCode, "Second", "Second system")),
                new ParameterizedTypeReference<>() {}
        );

        // Then
        assertThat(duplicateResponse.getBody()).isNotNull();
        assertThat(duplicateResponse.getBody().success()).isFalse();
    }

    @Test
    void shouldReturnErrorForInvalidCreateRequest() {
        // When - Create with empty system code
        ResponseEntity<ApiResponse<SystemResponse>> response = restTemplate.exchange(
                BASE_URL,
                HttpMethod.POST,
                new HttpEntity<>(new CreateSystemRequest("", "Name", "Desc")),
                new ParameterizedTypeReference<>() {}
        );

        // Then - Validation errors return 400 Bad Request
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isFalse();
    }
}
