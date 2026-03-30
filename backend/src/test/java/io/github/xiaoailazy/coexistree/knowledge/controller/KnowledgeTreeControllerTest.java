package io.github.xiaoailazy.coexistree.knowledge.controller;

import io.github.xiaoailazy.coexistree.shared.api.ApiResponse;
import io.github.xiaoailazy.coexistree.knowledge.dto.KnowledgeTreeStatusResponse;
import io.github.xiaoailazy.coexistree.knowledge.entity.SystemKnowledgeTreeEntity;
import io.github.xiaoailazy.coexistree.knowledge.repository.SystemKnowledgeTreeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeTreeControllerTest {

    @Mock
    private SystemKnowledgeTreeRepository systemKnowledgeTreeRepository;

    private KnowledgeTreeController controller;

    @BeforeEach
    void setUp() {
        controller = new KnowledgeTreeController(systemKnowledgeTreeRepository);
    }

    private SystemKnowledgeTreeEntity createTestEntity(Long systemId, Integer version, Integer nodeCount, String status) {
        SystemKnowledgeTreeEntity entity = new SystemKnowledgeTreeEntity();
        entity.setId(1L);
        entity.setSystemId(systemId);
        entity.setTreeFilePath("data/system-trees/test/system_tree.json");
        entity.setTreeVersion(version);
        entity.setNodeCount(nodeCount);
        entity.setTreeStatus(status);
        entity.setCreatedAt(LocalDateTime.of(2025, 1, 1, 10, 0));
        entity.setUpdatedAt(LocalDateTime.of(2025, 1, 15, 14, 30));
        return entity;
    }

    @Test
    void testGetStatus_WhenTreeExists() {
        Long systemId = 1L;
        SystemKnowledgeTreeEntity entity = createTestEntity(systemId, 5, 42, "ACTIVE");

        when(systemKnowledgeTreeRepository.findBySystemId(systemId)).thenReturn(Optional.of(entity));

        ApiResponse<KnowledgeTreeStatusResponse> response = controller.getStatus(systemId);

        assertThat(response).isNotNull();
        assertThat(response.success()).isTrue();
        assertThat(response.data()).isNotNull();
        assertThat(response.data().treeVersion()).isEqualTo(5);
        assertThat(response.data().nodeCount()).isEqualTo(42);
        assertThat(response.data().treeStatus()).isEqualTo("ACTIVE");
        assertThat(response.data().lastUpdatedAt()).isEqualTo(LocalDateTime.of(2025, 1, 15, 14, 30));
    }

    @Test
    void testGetStatus_WhenTreeDoesNotExist() {
        Long systemId = 999L;

        when(systemKnowledgeTreeRepository.findBySystemId(systemId)).thenReturn(Optional.empty());

        ApiResponse<KnowledgeTreeStatusResponse> response = controller.getStatus(systemId);

        assertThat(response).isNotNull();
        assertThat(response.success()).isTrue();
        assertThat(response.data()).isNotNull();
        assertThat(response.data().treeVersion()).isEqualTo(0);
        assertThat(response.data().nodeCount()).isEqualTo(0);
        assertThat(response.data().treeStatus()).isEqualTo("EMPTY");
        assertThat(response.data().lastUpdatedAt()).isNull();
    }

    @Test
    void testGetStatus_WithBuildingStatus() {
        Long systemId = 2L;
        SystemKnowledgeTreeEntity entity = createTestEntity(systemId, 1, 0, "BUILDING");

        when(systemKnowledgeTreeRepository.findBySystemId(systemId)).thenReturn(Optional.of(entity));

        ApiResponse<KnowledgeTreeStatusResponse> response = controller.getStatus(systemId);

        assertThat(response).isNotNull();
        assertThat(response.success()).isTrue();
        assertThat(response.data()).isNotNull();
        assertThat(response.data().treeVersion()).isEqualTo(1);
        assertThat(response.data().nodeCount()).isEqualTo(0);
        assertThat(response.data().treeStatus()).isEqualTo("BUILDING");
    }

    @Test
    void testGetStatus_WithEmptyStatus() {
        Long systemId = 3L;
        SystemKnowledgeTreeEntity entity = createTestEntity(systemId, 0, 0, "EMPTY");

        when(systemKnowledgeTreeRepository.findBySystemId(systemId)).thenReturn(Optional.of(entity));

        ApiResponse<KnowledgeTreeStatusResponse> response = controller.getStatus(systemId);

        assertThat(response).isNotNull();
        assertThat(response.success()).isTrue();
        assertThat(response.data()).isNotNull();
        assertThat(response.data().treeVersion()).isEqualTo(0);
        assertThat(response.data().nodeCount()).isEqualTo(0);
        assertThat(response.data().treeStatus()).isEqualTo("EMPTY");
    }
}
