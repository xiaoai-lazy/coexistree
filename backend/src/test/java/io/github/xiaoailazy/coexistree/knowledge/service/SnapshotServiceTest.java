package io.github.xiaoailazy.coexistree.knowledge.service;

import io.github.xiaoailazy.coexistree.shared.util.JsonUtils;
import io.github.xiaoailazy.coexistree.knowledge.entity.SystemTreeSnapshotEntity;
import io.github.xiaoailazy.coexistree.knowledge.model.SystemKnowledgeTree;
import io.github.xiaoailazy.coexistree.knowledge.repository.SystemTreeSnapshotRepository;
import io.github.xiaoailazy.coexistree.indexer.model.TreeNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SnapshotServiceTest {

    @Mock
    private SystemTreeSnapshotRepository snapshotRepository;
    @Mock
    private JsonUtils jsonUtils;

    private SnapshotService snapshotService;

    @BeforeEach
    void setUp() {
        snapshotService = new SnapshotService(snapshotRepository, null, jsonUtils);
    }

    @Test
    void testCreateSnapshot() {
        // Given
        Long systemId = 1L;
        Long triggeredByDocId = 100L;

        TreeNode node1 = createTreeNode("1", "节点1");
        TreeNode node2 = createTreeNode("2", "节点2");
        List<TreeNode> structure = List.of(node1, node2);

        SystemKnowledgeTree systemTree = new SystemKnowledgeTree();
        systemTree.setSystemId(systemId);
        systemTree.setSystemCode("test");
        systemTree.setStructure(structure);

        String treeJson = "{\"systemId\":1,\"nodeCount\":2}";
        when(jsonUtils.toJson(systemTree)).thenReturn(treeJson);

        // When
        SystemTreeSnapshotEntity result = snapshotService.createSnapshot(systemTree, triggeredByDocId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getSystemId()).isEqualTo(systemId);
        assertThat(result.getTriggeredByDocId()).isEqualTo(triggeredByDocId);
        assertThat(result.getTriggeredBy()).isEqualTo("SYSTEM");
        assertThat(result.getNodeCount()).isEqualTo(2);
        assertThat(result.getTreeJson()).isEqualTo(treeJson);
        assertThat(result.getIsPinned()).isFalse();
        assertThat(result.getStatus()).isEqualTo("ACTIVE");
        assertThat(result.getSnapshotName()).startsWith("tree-");

        verify(snapshotRepository).save(any(SystemTreeSnapshotEntity.class));
    }

    @Test
    void testCreateSnapshotWithEmptyStructure() {
        // Given
        SystemKnowledgeTree systemTree = new SystemKnowledgeTree();
        systemTree.setSystemId(1L);
        systemTree.setStructure(List.of());

        when(jsonUtils.toJson(systemTree)).thenReturn("{}");

        // When
        SystemTreeSnapshotEntity result = snapshotService.createSnapshot(systemTree, null);

        // Then
        assertThat(result.getNodeCount()).isEqualTo(0);
        verify(snapshotRepository).save(any(SystemTreeSnapshotEntity.class));
    }

    @Test
    void testCreateSnapshotWithNestedStructure() {
        // Given
        TreeNode child1 = createTreeNode("1.1", "子节点1");
        TreeNode child2 = createTreeNode("1.2", "子节点2");
        TreeNode root = createTreeNode("1", "根节点", List.of(child1, child2));

        SystemKnowledgeTree systemTree = new SystemKnowledgeTree();
        systemTree.setSystemId(1L);
        systemTree.setStructure(List.of(root));

        when(jsonUtils.toJson(systemTree)).thenReturn("{\"nodeCount\":3}");

        // When
        SystemTreeSnapshotEntity result = snapshotService.createSnapshot(systemTree, 200L);

        // Then
        assertThat(result.getNodeCount()).isEqualTo(3);
    }

    @Test
    void testCreateSnapshotVerifyNameFormat() {
        // Given
        SystemKnowledgeTree systemTree = new SystemKnowledgeTree();
        systemTree.setSystemId(1L);
        systemTree.setStructure(List.of());

        when(jsonUtils.toJson(systemTree)).thenReturn("{}");

        // When
        SystemTreeSnapshotEntity result = snapshotService.createSnapshot(systemTree, null);

        // Then
        assertThat(result.getSnapshotName())
                .matches("tree-\\d{4}-\\d{2}-\\d{2}-\\d{2}-\\d{2}");
    }

    @Test
    void testGetAvailableSnapshots() {
        // Given
        Long systemId = 1L;

        SystemTreeSnapshotEntity active1 = createSnapshotEntity("tree-2024-01-01-10-00", "ACTIVE", 10);
        SystemTreeSnapshotEntity active2 = createSnapshotEntity("tree-2024-01-02-10-00", "ACTIVE", 20);
        SystemTreeSnapshotEntity deleted = createSnapshotEntity("tree-2024-01-03-10-00", "DELETED", 15);

        when(snapshotRepository.findBySystemIdOrderByCreatedAtDesc(systemId))
                .thenReturn(List.of(deleted, active2, active1));

        // When
        List<SnapshotService.SnapshotItem> result = snapshotService.getAvailableSnapshots(systemId);

        // Then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getSnapshotName()).isEqualTo("tree-2024-01-02-10-00");
        assertThat(result.get(1).getSnapshotName()).isEqualTo("tree-2024-01-01-10-00");
    }

    @Test
    void testGetAvailableSnapshotsEmptyList() {
        // Given
        Long systemId = 1L;
        when(snapshotRepository.findBySystemIdOrderByCreatedAtDesc(systemId))
                .thenReturn(List.of());

        // When
        List<SnapshotService.SnapshotItem> result = snapshotService.getAvailableSnapshots(systemId);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void testGetAvailableSnapshotsAllDeleted() {
        // Given
        Long systemId = 1L;

        SystemTreeSnapshotEntity deleted1 = createSnapshotEntity("tree-1", "DELETED", 10);
        SystemTreeSnapshotEntity deleted2 = createSnapshotEntity("tree-2", "DELETED", 20);

        when(snapshotRepository.findBySystemIdOrderByCreatedAtDesc(systemId))
                .thenReturn(List.of(deleted1, deleted2));

        // When
        List<SnapshotService.SnapshotItem> result = snapshotService.getAvailableSnapshots(systemId);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void testSnapshotItemProperties() {
        // Given
        Long systemId = 1L;
        LocalDateTime createdAt = LocalDateTime.of(2024, 1, 1, 10, 0);

        SystemTreeSnapshotEntity snapshot = new SystemTreeSnapshotEntity();
        snapshot.setSnapshotName("test-snapshot");
        snapshot.setCreatedAt(createdAt);
        snapshot.setTriggeredBy("USER");
        snapshot.setNodeCount(100);
        snapshot.setIsPinned(true);
        snapshot.setStatus("ACTIVE");

        when(snapshotRepository.findBySystemIdOrderByCreatedAtDesc(systemId))
                .thenReturn(List.of(snapshot));

        // When
        List<SnapshotService.SnapshotItem> result = snapshotService.getAvailableSnapshots(systemId);

        // Then
        assertThat(result).hasSize(1);
        SnapshotService.SnapshotItem item = result.get(0);
        assertThat(item.getSnapshotName()).isEqualTo("test-snapshot");
        assertThat(item.getCreatedAt()).isEqualTo(createdAt);
        assertThat(item.getTriggeredBy()).isEqualTo("USER");
        assertThat(item.getNodeCount()).isEqualTo(100);
        assertThat(item.getIsPinned()).isTrue();
    }

    private TreeNode createTreeNode(String nodeId, String title) {
        TreeNode node = new TreeNode();
        node.setNodeId(nodeId);
        node.setTitle(title);
        node.setLevel(nodeId.split("\\.").length);
        return node;
    }

    private TreeNode createTreeNode(String nodeId, String title, List<TreeNode> children) {
        TreeNode node = createTreeNode(nodeId, title);
        node.setNodes(children);
        return node;
    }

    private SystemTreeSnapshotEntity createSnapshotEntity(String name, String status, int nodeCount) {
        SystemTreeSnapshotEntity entity = new SystemTreeSnapshotEntity();
        entity.setSnapshotName(name);
        entity.setStatus(status);
        entity.setNodeCount(nodeCount);
        entity.setTriggeredBy("SYSTEM");
        entity.setIsPinned(false);
        entity.setCreatedAt(LocalDateTime.now());
        return entity;
    }
}
