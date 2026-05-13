package io.github.xiaoailazy.coexistree.knowledge.service;

import io.github.xiaoailazy.coexistree.shared.util.JsonUtils;
import io.github.xiaoailazy.coexistree.knowledge.entity.SystemTreeSnapshotEntity;
import io.github.xiaoailazy.coexistree.knowledge.model.SystemKnowledgeTree;
import io.github.xiaoailazy.coexistree.knowledge.repository.SystemTreeSnapshotRepository;
import io.github.xiaoailazy.coexistree.indexer.model.TreeNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
        snapshotService = new SnapshotService(snapshotRepository, jsonUtils);
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
}
