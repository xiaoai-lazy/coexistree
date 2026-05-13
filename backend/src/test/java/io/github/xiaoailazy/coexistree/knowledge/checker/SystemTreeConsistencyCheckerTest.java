package io.github.xiaoailazy.coexistree.knowledge.checker;

import io.github.xiaoailazy.coexistree.knowledge.entity.SystemKnowledgeTreeEntity;
import io.github.xiaoailazy.coexistree.knowledge.model.SystemKnowledgeTree;
import io.github.xiaoailazy.coexistree.knowledge.repository.SystemKnowledgeTreeRepository;
import io.github.xiaoailazy.coexistree.indexer.model.TreeNode;
import io.github.xiaoailazy.coexistree.indexer.tree.TreeNodeCounter;
import io.github.xiaoailazy.coexistree.shared.util.JsonUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SystemTreeConsistencyCheckerTest {

    @Mock
    private SystemKnowledgeTreeRepository repository;

    @Mock
    private TreeNodeCounter nodeCounter;

    @Mock
    private JsonUtils jsonUtils;

    private SystemTreeConsistencyChecker checker;

    @BeforeEach
    void setUp() {
        checker = new SystemTreeConsistencyChecker(repository, nodeCounter, jsonUtils);
    }

    @Test
    void checkAllTrees_shouldProcessAllRecords() {
        SystemKnowledgeTree tree = createTree(1L, "sys1", 1, 10);
        List<SystemKnowledgeTreeEntity> entities = List.of(
                createEntity(1L, "{}", 1, 10, "ACTIVE")
        );
        when(repository.findAll()).thenReturn(entities);
        when(jsonUtils.fromJson("{}", SystemKnowledgeTree.class)).thenReturn(tree);
        when(nodeCounter.count(any())).thenReturn(10);

        checker.checkAllTrees();

        verify(repository, times(1)).findAll();
        verify(repository, never()).save(any());
    }

    @Test
    void checkTree_invalidJson_shouldMarkAsEmpty() {
        SystemKnowledgeTreeEntity entity = createEntity(1L, "bad", 1, 10, "ACTIVE");
        when(repository.findAll()).thenReturn(List.of(entity));
        when(jsonUtils.fromJson("bad", SystemKnowledgeTree.class)).thenThrow(new RuntimeException("Invalid JSON"));

        checker.checkAllTrees();

        verify(repository).save(argThat(e ->
            e.getSystemId().equals(1L) && "EMPTY".equals(e.getTreeStatus())
        ));
    }

    @Test
    void checkTree_nodeCountMismatch_shouldUpdateDatabase() {
        SystemKnowledgeTreeEntity entity = createEntity(1L, "{}", 1, 10, "ACTIVE");
        SystemKnowledgeTree tree = createTree(1L, "sys1", 1, 15);

        when(repository.findAll()).thenReturn(List.of(entity));
        when(jsonUtils.fromJson("{}", SystemKnowledgeTree.class)).thenReturn(tree);
        when(nodeCounter.count(any())).thenReturn(15);

        checker.checkAllTrees();

        verify(repository).save(argThat(e ->
            e.getSystemId().equals(1L) && e.getNodeCount().equals(15)
        ));
    }

    @Test
    void checkTree_versionMismatch_shouldLogWarning() {
        SystemKnowledgeTreeEntity entity = createEntity(1L, "{}", 1, 10, "ACTIVE");
        SystemKnowledgeTree tree = createTree(1L, "sys1", 2, 10);

        when(repository.findAll()).thenReturn(List.of(entity));
        when(jsonUtils.fromJson("{}", SystemKnowledgeTree.class)).thenReturn(tree);
        when(nodeCounter.count(any())).thenReturn(10);

        checker.checkAllTrees();

        verify(repository, never()).save(any());
    }

    @Test
    void checkTree_allConsistent_shouldNotUpdate() {
        SystemKnowledgeTreeEntity entity = createEntity(1L, "{}", 1, 10, "ACTIVE");
        SystemKnowledgeTree tree = createTree(1L, "sys1", 1, 10);

        when(repository.findAll()).thenReturn(List.of(entity));
        when(jsonUtils.fromJson("{}", SystemKnowledgeTree.class)).thenReturn(tree);
        when(nodeCounter.count(any())).thenReturn(10);

        checker.checkAllTrees();

        verify(repository, never()).save(any());
    }

    private SystemKnowledgeTreeEntity createEntity(Long systemId, String path, Integer version, Integer nodeCount, String status) {
        SystemKnowledgeTreeEntity entity = new SystemKnowledgeTreeEntity();
        entity.setSystemId(systemId);
        entity.setTreeJson(path);
        entity.setTreeVersion(version);
        entity.setNodeCount(nodeCount);
        entity.setTreeStatus(status);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        return entity;
    }

    private SystemKnowledgeTree createTree(Long systemId, String systemCode, Integer version, int nodeCount) {
        SystemKnowledgeTree tree = new SystemKnowledgeTree();
        tree.setSystemId(systemId);
        tree.setSystemCode(systemCode);
        tree.setTreeVersion(version);

        List<TreeNode> structure = new ArrayList<>();
        for (int i = 0; i < nodeCount; i++) {
            TreeNode node = new TreeNode();
            node.setNodeId(systemCode + "_" + i);
            node.setTitle("Node " + i);
            structure.add(node);
        }
        tree.setStructure(structure);

        return tree;
    }
}
