package io.github.xiaoailazy.coexistree.knowledge.controller;

import io.github.xiaoailazy.coexistree.shared.api.ApiResponse;
import io.github.xiaoailazy.coexistree.shared.integration.AbstractIntegrationTest;
import io.github.xiaoailazy.coexistree.knowledge.dto.KnowledgeTreeStatusResponse;
import io.github.xiaoailazy.coexistree.knowledge.entity.SystemKnowledgeTreeEntity;
import io.github.xiaoailazy.coexistree.knowledge.repository.SystemKnowledgeTreeRepository;
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

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeTreeControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private SystemKnowledgeTreeRepository systemKnowledgeTreeRepository;

    private static final String SYSTEMS_URL = "/api/v1/systems";

    private Long systemId;

    @BeforeEach
    void setUp() {
        // Create a system with unique code for each test
        String uniqueCode = "TREE-" + UUID.randomUUID().toString().substring(0, 8);
        ResponseEntity<ApiResponse<SystemResponse>> response = restTemplate.exchange(
                SYSTEMS_URL,
                HttpMethod.POST,
                new HttpEntity<>(new CreateSystemRequest(uniqueCode, "Tree Test System", "For tree tests")),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isTrue();
        systemId = response.getBody().data().id();
    }

    @Test
    void shouldReturnEmptyStatusWhenTreeDoesNotExist() {
        // When
        ResponseEntity<ApiResponse<KnowledgeTreeStatusResponse>> response = restTemplate.exchange(
                SYSTEMS_URL + "/" + systemId + "/knowledge-tree/status",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isTrue();
        assertThat(response.getBody().data().treeVersion()).isZero();
        assertThat(response.getBody().data().nodeCount()).isZero();
        assertThat(response.getBody().data().treeStatus()).isEqualTo("EMPTY");
        assertThat(response.getBody().data().lastUpdatedAt()).isNull();
    }

    @Test
    void shouldReturnTreeStatusWhenTreeExists() {
        // Given - Create a knowledge tree directly in the database
        SystemKnowledgeTreeEntity tree = new SystemKnowledgeTreeEntity();
        tree.setSystemId(systemId);
        tree.setTreeFilePath("/data/system-trees/" + systemId + "/tree.json");
        tree.setTreeVersion(5);
        tree.setNodeCount(42);
        tree.setTreeStatus("ACTIVE");
        tree.setCreatedAt(LocalDateTime.now());
        tree.setUpdatedAt(LocalDateTime.now());
        systemKnowledgeTreeRepository.save(tree);

        // When
        ResponseEntity<ApiResponse<KnowledgeTreeStatusResponse>> response = restTemplate.exchange(
                SYSTEMS_URL + "/" + systemId + "/knowledge-tree/status",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isTrue();
        assertThat(response.getBody().data().treeVersion()).isEqualTo(5);
        assertThat(response.getBody().data().nodeCount()).isEqualTo(42);
        assertThat(response.getBody().data().treeStatus()).isEqualTo("ACTIVE");
        assertThat(response.getBody().data().lastUpdatedAt()).isNotNull();
    }

    @Test
    void shouldReturnBuildingStatus() {
        // Given - Create a tree with BUILDING status
        SystemKnowledgeTreeEntity tree = new SystemKnowledgeTreeEntity();
        tree.setSystemId(systemId);
        tree.setTreeFilePath("/data/system-trees/" + systemId + "/tree.json");
        tree.setTreeVersion(1);
        tree.setNodeCount(0);
        tree.setTreeStatus("BUILDING");
        tree.setCreatedAt(LocalDateTime.now());
        tree.setUpdatedAt(LocalDateTime.now());
        systemKnowledgeTreeRepository.save(tree);

        // When
        ResponseEntity<ApiResponse<KnowledgeTreeStatusResponse>> response = restTemplate.exchange(
                SYSTEMS_URL + "/" + systemId + "/knowledge-tree/status",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
        );

        // Then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().data().treeStatus()).isEqualTo("BUILDING");
        assertThat(response.getBody().data().nodeCount()).isZero();
    }
}
