package io.github.xiaoailazy.coexistree.knowledge.checker;

import io.github.xiaoailazy.coexistree.knowledge.entity.SystemKnowledgeTreeEntity;
import io.github.xiaoailazy.coexistree.knowledge.model.SystemKnowledgeTree;
import io.github.xiaoailazy.coexistree.knowledge.repository.SystemKnowledgeTreeRepository;
import io.github.xiaoailazy.coexistree.knowledge.storage.SystemTreeFileLoader;
import io.github.xiaoailazy.coexistree.indexer.model.TreeNode;
import io.github.xiaoailazy.coexistree.indexer.tree.TreeNodeCounter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
    private SystemTreeFileLoader fileLoader;

    @Mock
    private TreeNodeCounter nodeCounter;

    @TempDir
    Path tempDir;

    private SystemTreeConsistencyChecker checker;

    @BeforeEach
    void setUp() {
        checker = new SystemTreeConsistencyChecker(repository, fileLoader, nodeCounter);
    }

    @Test
    void checkAllTrees_shouldProcessAllRecords() throws IOException {
        // Given - 使用真实存在的临时文件
        Path treePath = createRealTreeFile(1, 10);
        List<SystemKnowledgeTreeEntity> entities = List.of(
                createEntity(1L, treePath.toString(), 1, 10, "ACTIVE")
        );
        when(repository.findAll()).thenReturn(entities);
        when(fileLoader.load(any(Path.class))).thenReturn(createTree(1L, "sys1", 1, 10));
        when(nodeCounter.count(any())).thenReturn(10);

        // When
        checker.checkAllTrees();

        // Then
        verify(repository, times(1)).findAll();
        verify(repository, never()).save(any()); // 一致性检查通过，不需要保存
    }

    @Test
    void checkTree_fileNotExists_shouldMarkAsEmpty() {
        // Given - 使用不存在的路径
        SystemKnowledgeTreeEntity entity = createEntity(1L, "/nonexistent/path/system_tree.json", 1, 10, "ACTIVE");
        when(repository.findAll()).thenReturn(List.of(entity));

        // When
        checker.checkAllTrees();

        // Then
        verify(repository).save(argThat(e ->
            e.getSystemId().equals(1L) && "EMPTY".equals(e.getTreeStatus())
        ));
    }

    @Test
    void checkTree_invalidJson_shouldMarkAsEmpty() throws IOException {
        // Given - 使用真实存在的文件，但 fileLoader 会抛出异常
        Path treePath = createRealTreeFile(1, 10);
        SystemKnowledgeTreeEntity entity = createEntity(1L, treePath.toString(), 1, 10, "ACTIVE");
        when(repository.findAll()).thenReturn(List.of(entity));
        when(fileLoader.load(any(Path.class))).thenThrow(new RuntimeException("Invalid JSON"));

        // When
        checker.checkAllTrees();

        // Then
        verify(repository).save(argThat(e ->
            e.getSystemId().equals(1L) && "EMPTY".equals(e.getTreeStatus())
        ));
    }

    @Test
    void checkTree_nodeCountMismatch_shouldUpdateDatabase() throws IOException {
        // Given - DB 中是 10 个节点，实际文件中有 15 个
        Path treePath = createRealTreeFile(1, 10);
        SystemKnowledgeTreeEntity entity = createEntity(1L, treePath.toString(), 1, 10, "ACTIVE");
        SystemKnowledgeTree tree = createTree(1L, "sys1", 1, 15);

        when(repository.findAll()).thenReturn(List.of(entity));
        when(fileLoader.load(any(Path.class))).thenReturn(tree);
        when(nodeCounter.count(any())).thenReturn(15);  // 实际有 15 个节点

        // When
        checker.checkAllTrees();

        // Then - nodeCount 不匹配，更新数据库
        verify(repository).save(argThat(e ->
            e.getSystemId().equals(1L) && e.getNodeCount().equals(15)
        ));
    }

    @Test
    void checkTree_versionMismatch_shouldLogWarning() throws IOException {
        // Given - DB 版本是 1，文件版本是 2，但 nodeCount 一致
        Path treePath = createRealTreeFile(1, 10);
        SystemKnowledgeTreeEntity entity = createEntity(1L, treePath.toString(), 1, 10, "ACTIVE");
        SystemKnowledgeTree tree = createTree(1L, "sys1", 2, 10);

        when(repository.findAll()).thenReturn(List.of(entity));
        when(fileLoader.load(any(Path.class))).thenReturn(tree);
        when(nodeCounter.count(any())).thenReturn(10);  // nodeCount 与 DB 一致

        // When
        checker.checkAllTrees();

        // Then - version mismatch 只记录警告，不保存（因为 nodeCount 一致）
        verify(repository, never()).save(any());
    }

    @Test
    void checkTree_allConsistent_shouldNotUpdate() throws IOException {
        // Given - 完全一致的记录
        Path treePath = createRealTreeFile(1, 10);
        SystemKnowledgeTreeEntity entity = createEntity(1L, treePath.toString(), 1, 10, "ACTIVE");
        SystemKnowledgeTree tree = createTree(1L, "sys1", 1, 10);

        when(repository.findAll()).thenReturn(List.of(entity));
        when(fileLoader.load(any(Path.class))).thenReturn(tree);
        when(nodeCounter.count(any())).thenReturn(10);

        // When
        checker.checkAllTrees();

        // Then
        verify(repository, never()).save(any());
    }

    private Path createRealTreeFile(int version, int nodeCount) throws IOException {
        Path treeFile = tempDir.resolve("system_tree_" + version + ".json");
        // 写入一个有效的 JSON
        String json = "{\"systemId\":1,\"systemCode\":\"sys1\",\"treeVersion\":" + version + "}";
        Files.writeString(treeFile, json);
        return treeFile;
    }

    private SystemKnowledgeTreeEntity createEntity(Long systemId, String path, Integer version, Integer nodeCount, String status) {
        SystemKnowledgeTreeEntity entity = new SystemKnowledgeTreeEntity();
        entity.setSystemId(systemId);
        entity.setTreeFilePath(path);
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
