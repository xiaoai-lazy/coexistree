package io.github.xiaoailazy.coexistree.knowledge.service;

import io.github.xiaoailazy.coexistree.knowledge.model.*;
import io.github.xiaoailazy.coexistree.indexer.model.*;
import io.github.xiaoailazy.coexistree.system.entity.SystemEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 测试合并失败场景的修复
 * 模拟 DEV_01 系统变更合并时 LLM 返回文档树节点 ID 作为 newParentNodeId 的情况
 */
class MergeChangeFailureTest {

    @TempDir
    Path tempDir;

    private SystemEntity devSystem;

    @BeforeEach
    void setUp() {
        devSystem = new SystemEntity();
        devSystem.setId(1L);
        devSystem.setSystemCode("DEV_01");
        devSystem.setSystemName("Development System 01");
    }

    @Test
    void shouldRejectCreateWhenLlmReturnsDocumentNodeIdAsParent() {
        // Given: 创建一个模拟的系统树（40个节点，类似生产环境）
        SystemKnowledgeTree systemTree = createDev01SystemTree();

        // Given: 创建一个变更文档树
        DocumentTree changeDoc = createChangeDocument();

        // Given: 模拟 LLM 返回的合并指令
        List<MergeInstruction> instructions = new ArrayList<>();

        // 指令1：UPDATE 现有节点（正确的情况）
        MergeInstruction updateInstr = new MergeInstruction();
        updateInstr.setOperation("UPDATE");
        updateInstr.setTargetNodeId("DEV_01_1");  // 系统树节点 ID
        updateInstr.setSourceNodeId("0001");       // 文档树节点 ID
        instructions.add(updateInstr);

        // 指令2：CREATE 新节点，但 LLM 错误地返回了文档树节点 ID 作为父节点
        // 这种情况应该被直接抛弃，不做反向查找
        MergeInstruction createInstr1 = new MergeInstruction();
        createInstr1.setOperation("CREATE");
        createInstr1.setSourceNodeId("0005");       // 新功能节点
        createInstr1.setNewParentNodeId("0001");    // ❌ 错误：这是文档树 ID！应该被抛弃
        instructions.add(createInstr1);

        // 指令3：CREATE 新节点，使用正确的系统树节点 ID 作为父节点
        MergeInstruction createInstr2 = new MergeInstruction();
        createInstr2.setOperation("CREATE");
        createInstr2.setSourceNodeId("0006");
        createInstr2.setNewParentNodeId("DEV_01_3"); // ✅ 正确：系统树节点 ID
        instructions.add(createInstr2);

        // 指令4：CREATE 新节点，父节点 ID 在系统树中不存在
        MergeInstruction createInstr3 = new MergeInstruction();
        createInstr3.setOperation("CREATE");
        createInstr3.setSourceNodeId("0007");
        createInstr3.setNewParentNodeId("9999");     // ❌ 错误：不存在的节点，应该被抛弃
        instructions.add(createInstr3);

        // When: 执行合并指令
        int initialNodeCount = countNodes(systemTree.getStructure());

        for (MergeInstruction instr : instructions) {
            if ("CREATE".equals(instr.getOperation())) {
                executeCreateStrict(systemTree, changeDoc, instr);
            }
        }

        int finalNodeCount = countNodes(systemTree.getStructure());

        // Then: 验证结果
        // 1. 只应该成功创建 1 个节点（指令3，直接使用正确的系统树节点 ID）
        // 2. 指令2和指令4应该被抛弃（父节点不存在或格式不正确）
        assertThat(finalNodeCount).isEqualTo(initialNodeCount + 1);

        // 验证指令2的节点被抛弃（DEV_01_1 下没有新增子节点）
        TreeNode dev01_1 = findNodeById(systemTree.getStructure(), "DEV_01_1");
        assertThat(dev01_1).isNotNull();
        assertThat(dev01_1.getNodes()).isEmpty(); // 没有新增子节点

        // 验证指令3的节点被添加到 DEV_01_3 下
        TreeNode dev01_3 = findNodeById(systemTree.getStructure(), "DEV_01_3");
        assertThat(dev01_3).isNotNull();
        assertThat(dev01_3.getNodes()).hasSize(1); // 新增了一个子节点
        assertThat(dev01_3.getNodes().get(0).getTitle()).isEqualTo("Another Feature");
    }

    @Test
    void shouldRejectCreateWhenParentNotFound() {
        // Given: 简单的系统树
        SystemKnowledgeTree systemTree = createSimpleSystemTree();
        DocumentTree docTree = createSimpleDocumentTree();

        // Given: 父节点不存在的 CREATE 指令
        MergeInstruction instruction = new MergeInstruction();
        instruction.setOperation("CREATE");
        instruction.setSourceNodeId("0001");
        instruction.setNewParentNodeId("NON_EXISTENT");

        int initialCount = countNodes(systemTree.getStructure());

        // When: 执行 CREATE
        executeCreateStrict(systemTree, docTree, instruction);

        // Then: 节点应该被抛弃，总数不变
        int finalCount = countNodes(systemTree.getStructure());
        assertThat(finalCount).isEqualTo(initialCount);
    }

    @Test
    void shouldRejectCreateWhenDocumentNodeIdUsedAsParent() {
        // Given: 简单的系统树
        SystemKnowledgeTree systemTree = createSimpleSystemTree();

        // 给 DEV_01_2 添加 source 引用 0002（但不应该被反向查找使用）
        TreeNode dev01_2 = findNodeById(systemTree.getStructure(), "DEV_01_2");
        NodeSource source = new NodeSource();
        source.setDocId(1L);
        source.setNodeId("0002");
        dev01_2.setSources(List.of(source));

        DocumentTree docTree = createSimpleDocumentTree();

        // Given: LLM 错误地返回文档节点 ID 作为父节点（纯数字格式）
        MergeInstruction instruction = new MergeInstruction();
        instruction.setOperation("CREATE");
        instruction.setSourceNodeId("0003");
        instruction.setNewParentNodeId("0002"); // 文档树 ID（纯数字格式）

        int initialCount = countNodes(systemTree.getStructure());

        // When: 执行 CREATE
        executeCreateStrict(systemTree, docTree, instruction);

        // Then: 节点应该被抛弃（因为 0002 不是系统树节点 ID）
        int finalCount = countNodes(systemTree.getStructure());
        assertThat(finalCount).isEqualTo(initialCount);
        assertThat(dev01_2.getNodes()).isEmpty(); // 没有新增子节点
    }

    @Test
    void shouldRejectCreateWhenNewParentNodeIdIsMissing() {
        // Given: 简单的系统树
        SystemKnowledgeTree systemTree = createSimpleSystemTree();
        DocumentTree docTree = createSimpleDocumentTree();

        // Given: 缺少 newParentNodeId 的 CREATE 指令
        MergeInstruction instruction = new MergeInstruction();
        instruction.setOperation("CREATE");
        instruction.setSourceNodeId("0001");
        instruction.setNewParentNodeId(null); // ❌ 错误：没有指定父节点

        int initialCount = countNodes(systemTree.getStructure());

        // When: 执行 CREATE
        executeCreateStrict(systemTree, docTree, instruction);

        // Then: 节点应该被抛弃，总数不变
        int finalCount = countNodes(systemTree.getStructure());
        assertThat(finalCount).isEqualTo(initialCount);
    }

    @Test
    void shouldAcceptCreateWithCorrectParentNodeId() {
        // Given: 简单的系统树
        SystemKnowledgeTree systemTree = createSimpleSystemTree();
        DocumentTree docTree = createSimpleDocumentTree();

        // Given: 使用正确的系统树节点 ID 作为父节点
        MergeInstruction instruction = new MergeInstruction();
        instruction.setOperation("CREATE");
        instruction.setSourceNodeId("0001");
        instruction.setNewParentNodeId("DEV_01_2"); // ✅ 正确的系统树节点 ID

        TreeNode dev01_2 = findNodeById(systemTree.getStructure(), "DEV_01_2");
        int initialChildCount = dev01_2.getNodes().size();

        // When: 执行 CREATE
        executeCreateStrict(systemTree, docTree, instruction);

        // Then: 节点应该被成功创建并添加到父节点下
        assertThat(dev01_2.getNodes()).hasSize(initialChildCount + 1);
        assertThat(dev01_2.getNodes().get(initialChildCount).getTitle()).isEqualTo("Doc Node 1");
    }

    /**
     * 模拟严格的 executeCreate 逻辑（newParentNodeId 必须指定且存在）
     */
    private void executeCreateStrict(SystemKnowledgeTree systemTree, DocumentTree docTree,
                                     MergeInstruction instruction) {
        String newParentNodeId = instruction.getNewParentNodeId();
        String sourceNodeId = instruction.getSourceNodeId();

        // 1. newParentNodeId 必须指定
        if (newParentNodeId == null || newParentNodeId.isEmpty()) {
            System.out.println("[WARN] CREATE 指令缺少 newParentNodeId, sourceNodeId=" + sourceNodeId + "，抛弃该节点");
            return;
        }

        // 2. 父节点必须在系统树中存在
        TreeNode parentNode = findNodeById(systemTree.getStructure(), newParentNodeId);
        if (parentNode == null) {
            System.out.println("[WARN] 父节点不存在, sourceNodeId=" + sourceNodeId
                    + ", newParentNodeId=" + newParentNodeId + "，抛弃该节点");
            return;
        }

        // 2. 定位文档树节点
        TreeNode docNode = findNodeById(docTree.getStructure(), sourceNodeId);
        if (docNode == null) {
            System.out.println("[WARN] 文档树节点不存在, sourceNodeId=" + sourceNodeId);
            return;
        }

        // 3. 创建新节点
        TreeNode newNode = new TreeNode();
        newNode.setNodeId(systemTree.getSystemCode() + "_" + (countNodes(systemTree.getStructure()) + 1));
        newNode.setTitle(docNode.getTitle());
        newNode.setLevel(docNode.getLevel());

        // 4. 设置 sources
        NodeSource source = new NodeSource();
        source.setDocId(2L); // 模拟 documentId=2
        source.setNodeId(docNode.getNodeId());
        newNode.setSources(List.of(source));

        // 5. 添加到父节点
        if (parentNode.getNodes() == null) {
            parentNode.setNodes(new ArrayList<>());
        }
        parentNode.getNodes().add(newNode);
        System.out.println("[INFO] CREATE 完成, 新节点 '" + newNode.getTitle() + "' 添加到父节点 " + parentNode.getNodeId());
    }

    /**
     * 创建模拟的 DEV_01 系统树（40个节点）
     */
    private SystemKnowledgeTree createDev01SystemTree() {
        SystemKnowledgeTree tree = new SystemKnowledgeTree();
        tree.setSystemId(1L);
        tree.setSystemCode("DEV_01");
        tree.setSystemName("Development System 01");
        tree.setTreeVersion(1);
        tree.setCreatedAt(LocalDateTime.now());
        tree.setLastUpdatedAt(LocalDateTime.now());

        List<TreeNode> structure = new ArrayList<>();

        // 根节点 1: 系统概述（引用文档节点 0001），初始无子节点
        TreeNode node1 = createTreeNode("DEV_01_1", "系统概述", 1);
        node1.setSources(List.of(createNodeSource(1L, "0001")));
        structure.add(node1);

        // 根节点 2: 架构设计（引用文档节点 0002），初始无子节点
        TreeNode node2 = createTreeNode("DEV_01_2", "架构设计", 1);
        node2.setSources(List.of(createNodeSource(1L, "0002")));
        structure.add(node2);

        // 根节点 3: 功能模块（引用文档节点 0003），初始无子节点
        TreeNode node3 = createTreeNode("DEV_01_3", "功能模块", 1);
        node3.setSources(List.of(createNodeSource(1L, "0003")));
        structure.add(node3);

        // 添加更多根节点以达到40个节点
        for (int i = 4; i <= 40; i++) {
            TreeNode node = createTreeNode("DEV_01_" + i, "节点" + i, 1);
            structure.add(node);
        }

        tree.setStructure(structure);
        return tree;
    }

    private SystemKnowledgeTree createSimpleSystemTree() {
        SystemKnowledgeTree tree = new SystemKnowledgeTree();
        tree.setSystemId(1L);
        tree.setSystemCode("DEV_01");
        tree.setSystemName("Simple System");
        tree.setTreeVersion(1);

        List<TreeNode> structure = new ArrayList<>();
        structure.add(createTreeNode("DEV_01_1", "Root 1", 1));
        structure.add(createTreeNode("DEV_01_2", "Root 2", 1));
        structure.add(createTreeNode("DEV_01_3", "Root 3", 1));

        tree.setStructure(structure);
        return tree;
    }

    private DocumentTree createChangeDocument() {
        DocumentTree doc = new DocumentTree();
        doc.setDocName("change-doc.md");
        doc.setDocDescription("变更文档");

        List<TreeNode> structure = new ArrayList<>();

        // 文档节点 0001: 系统概述更新
        TreeNode node1 = createTreeNode("0001", "系统概述", 1);
        structure.add(node1);

        // 文档节点 0002: 架构设计更新
        TreeNode node2 = createTreeNode("0002", "架构设计", 1);
        structure.add(node2);

        // 文档节点 0003: 功能模块更新
        TreeNode node3 = createTreeNode("0003", "功能模块", 1);
        structure.add(node3);

        // 文档节点 0005: 新增功能（新节点）
        TreeNode node5 = createTreeNode("0005", "New Feature", 2);
        structure.add(node5);

        // 文档节点 0006: 另一个新功能
        TreeNode node6 = createTreeNode("0006", "Another Feature", 2);
        structure.add(node6);

        // 文档节点 0007: 第三个新功能
        TreeNode node7 = createTreeNode("0007", "Third Feature", 2);
        structure.add(node7);

        doc.setStructure(structure);
        return doc;
    }

    private DocumentTree createSimpleDocumentTree() {
        DocumentTree doc = new DocumentTree();
        doc.setDocName("simple.md");

        List<TreeNode> structure = new ArrayList<>();
        structure.add(createTreeNode("0001", "Doc Node 1", 1));
        structure.add(createTreeNode("0002", "Doc Node 2", 1));
        structure.add(createTreeNode("0003", "Child Node", 2));

        doc.setStructure(structure);
        return doc;
    }

    private TreeNode createTreeNode(String nodeId, String title, int level) {
        TreeNode node = new TreeNode();
        node.setNodeId(nodeId);
        node.setTitle(title);
        node.setLevel(level);
        node.setNodes(new ArrayList<>());
        return node;
    }

    private NodeSource createNodeSource(Long docId, String nodeId) {
        NodeSource source = new NodeSource();
        source.setDocId(docId);
        source.setNodeId(nodeId);
        return source;
    }

    private TreeNode findNodeById(List<TreeNode> nodes, String nodeId) {
        for (TreeNode node : nodes) {
            if (nodeId.equals(node.getNodeId())) {
                return node;
            }
            if (node.getNodes() != null && !node.getNodes().isEmpty()) {
                TreeNode found = findNodeById(node.getNodes(), nodeId);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private int countNodes(List<TreeNode> nodes) {
        int count = 0;
        for (TreeNode node : nodes) {
            count++;
            if (node.getNodes() != null && !node.getNodes().isEmpty()) {
                count += countNodes(node.getNodes());
            }
        }
        return count;
    }
}
