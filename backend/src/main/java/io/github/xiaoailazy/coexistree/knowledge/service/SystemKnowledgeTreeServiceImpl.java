package io.github.xiaoailazy.coexistree.knowledge.service;

import io.github.xiaoailazy.coexistree.shared.entity.ProcessLogEntity;
import io.github.xiaoailazy.coexistree.shared.enums.ErrorCode;
import io.github.xiaoailazy.coexistree.shared.exception.BusinessException;
import io.github.xiaoailazy.coexistree.shared.repository.ProcessLogRepository;
import io.github.xiaoailazy.coexistree.shared.util.FilePathUtils;
import io.github.xiaoailazy.coexistree.shared.util.JsonUtils;
import io.github.xiaoailazy.coexistree.config.AppStorageProperties;
import io.github.xiaoailazy.coexistree.knowledge.entity.SystemKnowledgeTreeEntity;
import io.github.xiaoailazy.coexistree.knowledge.model.LlmTreeNode;
import io.github.xiaoailazy.coexistree.knowledge.model.MergeInstruction;
import io.github.xiaoailazy.coexistree.knowledge.model.SystemKnowledgeTree;
import io.github.xiaoailazy.coexistree.knowledge.model.SystemTreeStructure;
import io.github.xiaoailazy.coexistree.knowledge.repository.SystemKnowledgeTreeRepository;
import io.github.xiaoailazy.coexistree.knowledge.storage.SystemTreeFileLoader;
import io.github.xiaoailazy.coexistree.knowledge.storage.SystemTreeFileWriter;
import io.github.xiaoailazy.coexistree.knowledge.tree.SystemTreeNodeIdGenerator;
import io.github.xiaoailazy.coexistree.indexer.llm.LlmClient;
import io.github.xiaoailazy.coexistree.indexer.llm.PromptTemplateService;
import io.github.xiaoailazy.coexistree.indexer.llm.RetryableLlmService;
import io.github.xiaoailazy.coexistree.indexer.model.*;
import io.github.xiaoailazy.coexistree.indexer.summary.NodeSummaryService;
import io.github.xiaoailazy.coexistree.system.entity.SystemEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class SystemKnowledgeTreeServiceImpl implements SystemKnowledgeTreeService {

    private final SystemKnowledgeTreeRepository systemKnowledgeTreeRepository;
    private final SystemTreeFileLoader systemTreeFileLoader;
    private final SystemTreeFileWriter systemTreeFileWriter;
    private final PromptTemplateService promptTemplateService;
    private final LlmClient llmClient;
    private final RetryableLlmService retryableLlmService;
    private final JsonUtils jsonUtils;
    private final AppStorageProperties storageProperties;
    private final ProcessLogRepository processLogRepository;
    private final NodeSummaryService nodeSummaryService;
    private final SnapshotService snapshotService;

    public SystemKnowledgeTreeServiceImpl(
            SystemKnowledgeTreeRepository systemKnowledgeTreeRepository,
            SystemTreeFileLoader systemTreeFileLoader,
            SystemTreeFileWriter systemTreeFileWriter,
            PromptTemplateService promptTemplateService,
            LlmClient llmClient,
            RetryableLlmService retryableLlmService,
            JsonUtils jsonUtils,
            AppStorageProperties storageProperties,
            ProcessLogRepository processLogRepository,
            NodeSummaryService nodeSummaryService,
            SnapshotService snapshotService) {
        this.systemKnowledgeTreeRepository = systemKnowledgeTreeRepository;
        this.systemTreeFileLoader = systemTreeFileLoader;
        this.systemTreeFileWriter = systemTreeFileWriter;
        this.promptTemplateService = promptTemplateService;
        this.llmClient = llmClient;
        this.retryableLlmService = retryableLlmService;
        this.jsonUtils = jsonUtils;
        this.storageProperties = storageProperties;
        this.processLogRepository = processLogRepository;
        this.nodeSummaryService = nodeSummaryService;
        this.snapshotService = snapshotService;
    }

    @Override
    public SystemKnowledgeTree getActiveTree(Long systemId) {
        log.debug("获取活跃系统知识树, systemId={}", systemId);

        // 10.2.1.1 查询 system_knowledge_trees 表
        SystemKnowledgeTreeEntity entity = systemKnowledgeTreeRepository.findBySystemId(systemId)
                .orElseThrow(() -> {
                    log.error("系统知识树不存在, systemId={}", systemId);
                    return new BusinessException(ErrorCode.SYSTEM_TREE_NOT_FOUND,
                            "System knowledge tree not found for systemId: " + systemId);
                });

        // 10.2.1.2 校验 tree_status=ACTIVE
        if (!"ACTIVE".equals(entity.getTreeStatus())) {
            log.error("系统知识树状态不为 ACTIVE, systemId={}, status={}", systemId, entity.getTreeStatus());
            throw new BusinessException(ErrorCode.SYSTEM_TREE_NOT_READY,
                    "System knowledge tree is not ready, current status: " + entity.getTreeStatus());
        }

        // 10.2.1.3 加载系统树 JSON 文件（从相对路径解析）
        Path treePath = FilePathUtils.resolveSystemTreePath(storageProperties.systemTreeRoot(), entity.getTreeFilePath());
        SystemKnowledgeTree tree = systemTreeFileLoader.load(treePath);

        log.info("成功获取活跃系统知识树, systemId={}, treeVersion={}, nodeCount={}",
                systemId, entity.getTreeVersion(), entity.getNodeCount());

        return tree;
    }

    @Override
    public void mergeBaseline(Long documentId, DocumentTree docTree, SystemEntity system) {
        log.info("开始基线合并, documentId={}, systemId={}, systemCode={}",
                documentId, system.getId(), system.getSystemCode());

        // 10.2.2.1 检查系统树是否已存在
        Optional<SystemKnowledgeTreeEntity> existingOpt = systemKnowledgeTreeRepository.findBySystemId(system.getId());
        if (existingOpt.isPresent() && "ACTIVE".equals(existingOpt.get().getTreeStatus())) {
            log.info("系统树已存在且状态为 ACTIVE，转为变更合并, systemId={}", system.getId());
            mergeChange(documentId, docTree, system);
            return;
        }

        LocalDateTime now = LocalDateTime.now();

        try {
            // 10.2.2.2 准备 LLM 输入（提取文档树结构）
            log.debug("提取文档树结构用于 LLM 输入");
            String docTreeJson = extractStructureForLlm(docTree.getStructure());
            String prompt = promptTemplateService.buildBaselinePrompt(
                    system.getSystemName(),
                    system.getSystemCode(),
                    docTreeJson
            );

            // 10.2.2.3 调用 LLM 生成系统树结构（带重试）
            log.debug("调用 LLM 生成系统树结构");
            SystemTreeStructure llmOutput = retryableLlmService.generateSystemTreeStructure(prompt, null, 0.0);

            // 记录 LLM 响应日志
            saveProcessLog(documentId, "BASELINE_MERGE_LLM_RESPONSE", "SUCCESS",
                    "LLM generated structure with " + llmOutput.getStructure().size() + " root nodes");
            log.info("LLM 生成系统树结构成功, 根节点数={}", llmOutput.getStructure().size());

            // 10.2.2.4 转换 LLM 输出为 TreeNode（分配 nodeId、设置 sources、创建 provenance）
            log.debug("转换 LLM 输出为 TreeNode");
            SystemTreeNodeIdGenerator idGen = new SystemTreeNodeIdGenerator(system.getSystemCode());
            List<TreeNode> systemNodes = convertLlmOutputToTreeNodes(
                    llmOutput.getStructure(),
                    docTree,
                    documentId,
                    idGen,
                    now
            );

            // 构建 SystemKnowledgeTree
            SystemKnowledgeTree systemTree = new SystemKnowledgeTree();
            systemTree.setSystemId(system.getId());
            systemTree.setSystemCode(system.getSystemCode());
            systemTree.setSystemName(system.getSystemName());
            systemTree.setTreeVersion(1);
            systemTree.setDescription(llmOutput.getSystemDescription());
            systemTree.setCreatedAt(now);
            systemTree.setLastUpdatedAt(now);
            systemTree.setStructure(systemNodes);

            // 10.2.2.5 写入系统树 JSON 文件
            Path treePath = FilePathUtils.systemTreePath(
                    storageProperties.systemTreeRoot(),
                    system.getSystemCode()
            );
            log.debug("写入系统树文件, path={}", treePath);
            systemTreeFileWriter.write(treePath, systemTree);

            // 10.2.2.6 保存 system_knowledge_trees 记录
            int nodeCount = countNodes(systemNodes);
            SystemKnowledgeTreeEntity entity = new SystemKnowledgeTreeEntity();
            entity.setSystemId(system.getId());
            entity.setTreeFilePath(FilePathUtils.getRelativeSystemTreePath(system.getSystemCode()));
            entity.setTreeVersion(1);
            entity.setDescription(llmOutput.getSystemDescription());
            entity.setNodeCount(nodeCount);
            entity.setTreeStatus("ACTIVE");
            entity.setCreatedAt(now);
            entity.setUpdatedAt(now);
            systemKnowledgeTreeRepository.save(entity);

            // 10.2.2.7 记录处理日志
            saveProcessLog(documentId, "BASELINE_MERGE_COMPLETED", "SUCCESS",
                    String.format("System tree created, version=1, nodeCount=%d", nodeCount));

            // 10.2.2.8 创建快照
            snapshotService.createSnapshot(systemTree, documentId);

            log.info("基线合并完成, systemId={}, treeVersion=1, nodeCount={}",
                    system.getId(), nodeCount);

        } catch (Exception e) {
            log.error("基线合并失败, documentId={}, systemId={}", documentId, system.getId(), e);
            saveProcessLog(documentId, "BASELINE_MERGE_FAILED", "FAILED",
                    "Error: " + e.getMessage());
            throw new BusinessException(ErrorCode.SYSTEM_TREE_WRITE_FAILED,
                    "Failed to merge baseline: " + e.getMessage());
        }
    }

    @Override
    public void mergeChange(Long documentId, DocumentTree docTree, SystemEntity system) {
        log.info("开始变更合并, documentId={}, systemId={}, systemCode={}",
                documentId, system.getId(), system.getSystemCode());

        LocalDateTime now = LocalDateTime.now();

        try {
            // 10.2.3.1 加载现有系统树
            log.debug("加载现有系统树, systemId={}", system.getId());
            SystemKnowledgeTree systemTree = getActiveTree(system.getId());

            // 10.2.3.2 准备 LLM 输入（系统树结构 + 文档树结构）
            log.debug("准备 LLM 输入");
            String systemTreeJson = extractStructureForLlm(systemTree.getStructure());
            String docTreeJson = extractStructureForLlm(docTree.getStructure());
            String prompt = promptTemplateService.buildMergePrompt(systemTreeJson, docTreeJson);

            // 10.2.3.3 调用 LLM 生成合并指令（带重试）
            log.debug("调用 LLM 生成合并指令");
            List<MergeInstruction> instructions = retryableLlmService.generateMergeInstructions(prompt, null, 0.0);

            // 记录 LLM 响应日志
            saveProcessLog(documentId, "CHANGE_MERGE_LLM_RESPONSE", "SUCCESS",
                    "LLM generated " + instructions.size() + " merge instructions");
            log.info("LLM 生成合并指令成功, 指令数={}", instructions.size());

            // 10.2.3.4 执行合并指令（UPDATE/CREATE）
            log.debug("执行合并指令");
            SystemTreeNodeIdGenerator idGen = new SystemTreeNodeIdGenerator(system.getSystemCode());
            // 恢复计数器到当前最大值+1
            int maxCounter = findMaxNodeCounter(systemTree.getStructure(), system.getSystemCode());
            idGen.setCounter(maxCounter + 1);

            for (MergeInstruction instruction : instructions) {
                if ("UPDATE".equals(instruction.getOperation())) {
                    executeUpdate(systemTree, docTree, instruction, documentId, now);
                } else if ("CREATE".equals(instruction.getOperation())) {
                    executeCreate(systemTree, docTree, instruction, documentId, now, idGen);
                }
                // MOVE 和 DELETE 操作暂不实现
            }

            // 10.2.3.4.5 第二步：生成内容和变更记录
            log.debug("执行第二步：生成内容和变更记录");
            generateContentAndChangeLog(systemTree, docTree, documentId, now);

            // 更新系统树的 lastUpdatedAt
            systemTree.setLastUpdatedAt(now);

            // 10.2.3.5 计算新版本号并同步到内存模型
            SystemKnowledgeTreeEntity entity = systemKnowledgeTreeRepository.findBySystemId(system.getId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.SYSTEM_TREE_NOT_FOUND,
                            "System tree entity not found"));

            int newVersion = entity.getTreeVersion() + 1;
            int newNodeCount = countNodes(systemTree.getStructure());
            systemTree.setTreeVersion(newVersion);
            entity.setTreeVersion(newVersion);
            entity.setNodeCount(newNodeCount);
            entity.setUpdatedAt(now);

            // 10.2.3.6 原子写入系统树 JSON 文件
            Path treePath = FilePathUtils.systemTreePath(
                    storageProperties.systemTreeRoot(),
                    system.getSystemCode()
            );
            log.debug("原子写入系统树文件, path={}", treePath);
            systemTreeFileWriter.write(treePath, systemTree);

            // 10.2.3.7 更新数据库记录
            systemKnowledgeTreeRepository.save(entity);

            // 10.2.3.8 记录处理日志
            saveProcessLog(documentId, "CHANGE_MERGE_COMPLETED", "SUCCESS",
                    String.format("System tree updated, version=%d, nodeCount=%d, instructions=%d",
                            newVersion, newNodeCount, instructions.size()));

            // 10.2.3.8 创建快照
            snapshotService.createSnapshot(systemTree, documentId);

            log.info("变更合并完成, systemId={}, treeVersion={}, nodeCount={}, instructions={}",
                    system.getId(), newVersion, newNodeCount, instructions.size());

        } catch (Exception e) {
            log.error("变更合并失败, documentId={}, systemId={}", documentId, system.getId(), e);
            saveProcessLog(documentId, "CHANGE_MERGE_FAILED", "FAILED",
                    "Error: " + e.getMessage());
            throw new BusinessException(ErrorCode.SYSTEM_TREE_WRITE_FAILED,
                    "Failed to merge change: " + e.getMessage());
        }
    }

    /**
     * 提取文档树结构用于 LLM 输入
     * 只保留 nodeId, title, summary/prefixSummary 字段
     */
    private String extractStructureForLlm(List<TreeNode> nodes) {
        List<SimplifiedTreeNode> simplified = simplifyNodes(nodes);
        return jsonUtils.toJson(simplified);
    }

    /**
     * 简化树节点，只保留 LLM 需要的字段
     */
    private List<SimplifiedTreeNode> simplifyNodes(List<TreeNode> nodes) {
        List<SimplifiedTreeNode> result = new ArrayList<>();
        for (TreeNode node : nodes) {
            SimplifiedTreeNode simplified = new SimplifiedTreeNode();
            simplified.nodeId = node.getNodeId();
            simplified.title = node.getTitle();
            simplified.summary = node.getSummary();
            simplified.prefixSummary = node.getPrefixSummary();
            if (node.getNodes() != null && !node.getNodes().isEmpty()) {
                simplified.children = simplifyNodes(node.getNodes());
            }
            result.add(simplified);
        }
        return result;
    }

    /**
     * 转换 LLM 输出为 TreeNode
     */
    private List<TreeNode> convertLlmOutputToTreeNodes(
            List<LlmTreeNode> llmNodes,
            DocumentTree docTree,
            Long documentId,
            SystemTreeNodeIdGenerator idGen,
            LocalDateTime now) {
        List<TreeNode> result = new ArrayList<>();
        for (LlmTreeNode llmNode : llmNodes) {
            TreeNode sysNode = new TreeNode();
            sysNode.setNodeId(idGen.nextId());
            sysNode.setTitle(llmNode.getTitle());
            sysNode.setLevel(llmNode.getLevel());

            // 设置多来源引用
            List<NodeSource> sources = new ArrayList<>();
            if (llmNode.getSourceNodeIds() != null && !llmNode.getSourceNodeIds().isEmpty()) {
                for (String nodeId : llmNode.getSourceNodeIds()) {
                    NodeSource source = new NodeSource();
                    source.setDocId(documentId);
                    source.setNodeId(nodeId);
                    sources.add(source);
                }
                sysNode.setSources(sources);

                // text 生成逻辑
                if (llmNode.getSourceNodeIds().size() == 1) {
                    // 单个来源：直接复制文档树 text
                    String sourceNodeId = llmNode.getSourceNodeIds().get(0);
                    TreeNode docNode = findNodeById(docTree.getStructure(), sourceNodeId);
                    if (docNode != null && docNode.getText() != null) {
                        sysNode.setText(docNode.getText());
                    }
                } else {
                    // 多个来源：使用 LLM 整合多个来源的内容
                    List<String> sourceTexts = new ArrayList<>();
                    for (String nodeId : llmNode.getSourceNodeIds()) {
                        TreeNode docNode = findNodeById(docTree.getStructure(), nodeId);
                        if (docNode != null && docNode.getText() != null) {
                            sourceTexts.add(docNode.getText());
                        }
                    }
                    if (!sourceTexts.isEmpty()) {
                        String integratedText = integrateMultiSourceTextForBaseline(
                                llmNode.getTitle(),
                                sourceTexts,
                                docTree.getDocName()
                        );
                        sysNode.setText(integratedText);
                    }
                }
            }

            // 递归处理子节点（先处理子节点，后序遍历）
            if (llmNode.getChildren() != null && !llmNode.getChildren().isEmpty()) {
                sysNode.setNodes(convertLlmOutputToTreeNodes(llmNode.getChildren(), docTree, documentId, idGen, now));
            }

            // 无来源节点：汇总子节点内容生成 text
            if ((llmNode.getSourceNodeIds() == null || llmNode.getSourceNodeIds().isEmpty())
                    && sysNode.getNodes() != null && !sysNode.getNodes().isEmpty()) {
                String aggregatedText = aggregateChildNodeTexts(sysNode.getNodes(), sysNode.getTitle());
                sysNode.setText(aggregatedText);
            }

            // 创建 provenance
            KnowledgeNodeProvenance prov = new KnowledgeNodeProvenance();
            prov.setCreatedByDocId(documentId);
            prov.setCreatedAt(now);
            prov.setLastUpdatedByDocId(documentId);
            prov.setLastUpdatedAt(now);

            NodeChangeRecord changeRecord = new NodeChangeRecord();
            changeRecord.setDocId(documentId);
            changeRecord.setOperation("基线文档导入创建节点");
            changeRecord.setAt(now);
            prov.setChangeLog(List.of(changeRecord));

            sysNode.setProvenance(prov);

            result.add(sysNode);
        }
        return result;
    }

    /**
     * 统计节点总数（递归）
     */
    private int countNodes(List<TreeNode> nodes) {
        int count = 0;
        for (TreeNode node : nodes) {
            count++; // 当前节点
            if (node.getNodes() != null && !node.getNodes().isEmpty()) {
                count += countNodes(node.getNodes()); // 子节点
            }
        }
        return count;
    }

    /**
     * 查找系统树中最大的节点序号
     */
    private int findMaxNodeCounter(List<TreeNode> nodes, String systemCode) {
        int max = 0;
        for (TreeNode node : nodes) {
            if (node.getNodeId() != null && node.getNodeId().startsWith(systemCode + "_")) {
                try {
                    String counterStr = node.getNodeId().substring(systemCode.length() + 1);
                    int counter = Integer.parseInt(counterStr);
                    max = Math.max(max, counter);
                } catch (NumberFormatException e) {
                    log.warn("无法解析节点 ID 序号: {}", node.getNodeId());
                }
            }
            if (node.getNodes() != null && !node.getNodes().isEmpty()) {
                max = Math.max(max, findMaxNodeCounter(node.getNodes(), systemCode));
            }
        }
        return max;
    }

    /**
     * 在树中查找指定 ID 的节点（递归）
     */
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

    /**
     * 执行 UPDATE 操作（第一步：结构变更）
     */
    private void executeUpdate(SystemKnowledgeTree systemTree, DocumentTree docTree,
                               MergeInstruction instruction, Long documentId, LocalDateTime now) {
        log.debug("执行 UPDATE 操作, targetNodeId={}, sourceNodeId={}",
                instruction.getTargetNodeId(), instruction.getSourceNodeId());

        // 1. 定位系统树节点
        TreeNode sysNode = findNodeById(systemTree.getStructure(), instruction.getTargetNodeId());
        if (sysNode == null) {
            log.warn("系统树节点不存在, targetNodeId={}", instruction.getTargetNodeId());
            return;
        }

        // 2. 定位文档树节点
        TreeNode docNode = findNodeById(docTree.getStructure(), instruction.getSourceNodeId());
        if (docNode == null) {
            log.warn("文档树节点不存在, sourceNodeId={}", instruction.getSourceNodeId());
            return;
        }

        // 3. 更新 sources（追加新的 NodeSource）
        // 注意：不更新 text、summary、provenance（留到第二步）
        NodeSource newSource = new NodeSource();
        newSource.setDocId(documentId);
        newSource.setNodeId(docNode.getNodeId());

        if (sysNode.getSources() == null) {
            sysNode.setSources(new ArrayList<>());
        }
        // 检查是否已存在相同的引用
        boolean exists = sysNode.getSources().stream()
                .anyMatch(s -> documentId.equals(s.getDocId()) && docNode.getNodeId().equals(s.getNodeId()));
        if (!exists) {
            sysNode.getSources().add(newSource);
        }

        log.debug("UPDATE 操作完成, targetNodeId={}", instruction.getTargetNodeId());
    }

    /**
     * 执行 CREATE 操作（第一步：结构变更）
     */
    private void executeCreate(SystemKnowledgeTree systemTree, DocumentTree docTree,
                               MergeInstruction instruction, Long documentId, LocalDateTime now,
                               SystemTreeNodeIdGenerator idGen) {
        log.debug("执行 CREATE 操作, sourceNodeId={}, newParentNodeId={}",
                instruction.getSourceNodeId(), instruction.getNewParentNodeId());

        // 1. 定位父节点（必须在系统树中存在，且必须指定）
        String newParentNodeId = instruction.getNewParentNodeId();
        if (newParentNodeId == null || newParentNodeId.isEmpty()) {
            log.warn("CREATE 指令缺少 newParentNodeId, sourceNodeId={}，抛弃该节点",
                    instruction.getSourceNodeId());
            return;
        }

        TreeNode parentNode = findNodeById(systemTree.getStructure(), newParentNodeId);
        if (parentNode == null) {
            log.warn("父节点不存在, sourceNodeId={}, newParentNodeId={}，抛弃该节点",
                    instruction.getSourceNodeId(), newParentNodeId);
            return;
        }

        // 2. 定位文档树节点
        TreeNode docNode = findNodeById(docTree.getStructure(), instruction.getSourceNodeId());
        if (docNode == null) {
            log.warn("文档树节点不存在, sourceNodeId={}", instruction.getSourceNodeId());
            return;
        }

        // 3. 创建新节点
        TreeNode newNode = new TreeNode();
        newNode.setNodeId(idGen.nextId());
        newNode.setTitle(docNode.getTitle());
        newNode.setLevel(docNode.getLevel());
        // 注意：不设置 text、summary（留到第二步）

        // 4. 设置 sources
        NodeSource source = new NodeSource();
        source.setDocId(documentId);
        source.setNodeId(docNode.getNodeId());
        newNode.setSources(List.of(source));

        // 5. 创建 provenance（只设置创建时间）
        KnowledgeNodeProvenance prov = new KnowledgeNodeProvenance();
        prov.setCreatedByDocId(documentId);
        prov.setCreatedAt(now);
        newNode.setProvenance(prov);

        // 6. 添加到父节点
        if (parentNode != null) {
            if (parentNode.getNodes() == null) {
                parentNode.setNodes(new ArrayList<>());
            }
            parentNode.getNodes().add(newNode);
            log.debug("CREATE 操作完成, newNodeId={}, 添加到父节点 {}", newNode.getNodeId(), parentNode.getNodeId());
        } else {
            // 没有指定父节点的情况（正常应该通过 newParentNodeId 指定）
            // 添加到根级别
            systemTree.getStructure().add(newNode);
            log.debug("CREATE 操作完成, newNodeId={}, 添加到根级别（未指定父节点）", newNode.getNodeId());
        }
    }

    /**
     * 第二步：生成内容和变更记录
     */
    private void generateContentAndChangeLog(SystemKnowledgeTree systemTree, DocumentTree docTree,
                                         Long documentId, LocalDateTime now) {
        // 获取所有系统树节点
        List<TreeNode> allSystemNodes = new ArrayList<>();
        collectAllNodes(systemTree.getStructure(), allSystemNodes);

        for (TreeNode sysNode : allSystemNodes) {
            List<NodeSource> sources = sysNode.getSources();
            if (sources == null || sources.isEmpty()) {
                continue; // 跳过无 sources 的节点（LLM 新增的虚拟节点）
            }

            // 收集所有来源节点的 text
            List<String> sourceTexts = new ArrayList<>();
            for (NodeSource source : sources) {
                TreeNode docNode = findNodeById(docTree.getStructure(), source.getNodeId());
                if (docNode != null && docNode.getText() != null) {
                    sourceTexts.add(docNode.getText());
                }
            }

            if (sourceTexts.isEmpty()) {
                continue;
            }

            // 获取系统树旧 text
            String oldText = sysNode.getText();

            // LLM 整合内容（旧 text + 所有来源 text → 新 text）
            String integratedText = integrateMultiSourceText(
                sysNode.getTitle(),
                oldText,
                sourceTexts,
                docTree.getDocName()
            );

            // 覆盖更新 text
            sysNode.setText(integratedText);

            // 生成变更描述
            if (oldText != null && !oldText.equals(integratedText)) {
                String changeDesc = generateChangeDescription(
                    sysNode.getTitle(),
                    oldText,
                    integratedText,
                    docTree.getDocName()
                );

                NodeChangeRecord changeRecord = new NodeChangeRecord();
                changeRecord.setDocId(documentId);
                changeRecord.setOperation(changeDesc);
                changeRecord.setAt(now);

                if (sysNode.getProvenance() == null) {
                    KnowledgeNodeProvenance prov = new KnowledgeNodeProvenance();
                    prov.setCreatedByDocId(documentId);
                    prov.setCreatedAt(now);
                    prov.setChangeLog(new ArrayList<>());
                    sysNode.setProvenance(prov);
                }
                if (sysNode.getProvenance().getChangeLog() == null) {
                    sysNode.getProvenance().setChangeLog(new ArrayList<>());
                }
                sysNode.getProvenance().getChangeLog().add(changeRecord);
            }

            // 更新 provenance
            if (sysNode.getProvenance() != null) {
                sysNode.getProvenance().setLastUpdatedByDocId(documentId);
                sysNode.getProvenance().setLastUpdatedAt(now);
            }

            // 生成新的 summary（基于整合后的 text，使用动态长度）
            int level = sysNode.getLevel() != null ? sysNode.getLevel() : 3;
            String contentForSummary = integratedText;
            String summary = nodeSummaryService.summarizeNodeText(contentForSummary, level, null);
            sysNode.setSummary(summary);
        }

        log.info("第二步内容生成完成，处理节点数={}", allSystemNodes.size());
    }

    /**
     * LLM 整合多来源 text
     */
    private String integrateMultiSourceText(String title, String oldText, List<String> newTexts, String docName) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你需要将以下内容整合成一段完整、连贯的技术文档。\n\n");
        prompt.append("节点标题: ").append(title).append("\n\n");

        if (oldText != null && !oldText.isEmpty()) {
            prompt.append("系统树原有内容:\n").append(oldText).append("\n\n");
        }

        prompt.append("以下内容来自变更文档 \"").append(docName).append("\":\n\n");
        for (int i = 0; i < newTexts.size(); i++) {
            prompt.append("变更内容 ").append(i + 1).append(":\n").append(newTexts.get(i)).append("\n\n");
        }

        prompt.append("要求:\n");
        prompt.append("1. 整合所有内容，保留重要信息\n");
        prompt.append("2. 删除重复、矛盾或已废弃的内容\n");
        prompt.append("3. 按时间顺序反映内容的演进\n");
        prompt.append("4. 保持技术文档的专业性和完整性\n");
        prompt.append("5. 输出完整的整合后内容（不是摘要）\n\n");
        prompt.append("输出格式:\n");
        prompt.append("[整合后的完整内容]");

        return llmClient.chat(prompt.toString(), null, 0.0).content();
    }

    /**
     * 生成变更描述
     */
    private String generateChangeDescription(String title, String oldText, String newText, String docName) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("分析以下节点内容的变更，生成一段简洁的变更描述。\n\n");
        prompt.append("节点标题: ").append(title).append("\n");
        prompt.append("来源文档: ").append(docName).append("\n\n");

        prompt.append("原内容:\n").append(oldText).append("\n\n");
        prompt.append("新内容:\n").append(newText).append("\n\n");

        prompt.append("要求:\n");
        prompt.append("1. 用简洁的中文描述这次变更对模块/功能产生了什么影响\n");
        prompt.append("2. 说明新增/修改/删除了什么内容\n");
        prompt.append("3. 50-100字之间\n\n");
        prompt.append("输出格式:\n");
        prompt.append("[变更描述]");

        return llmClient.chat(prompt.toString(), null, 0.0).content();
    }

    /**
     * 收集树中所有节点（递归）
     */
    private void collectAllNodes(List<TreeNode> nodes, List<TreeNode> result) {
        for (TreeNode node : nodes) {
            result.add(node);
            if (node.getNodes() != null && !node.getNodes().isEmpty()) {
                collectAllNodes(node.getNodes(), result);
            }
        }
    }

    /**
     * 保存处理日志
     */
    private void saveProcessLog(Long documentId, String stage, String status, String message) {
        try {
            ProcessLogEntity log = new ProcessLogEntity();
            log.setEntityType("DOCUMENT");
            log.setEntityId(documentId);
            log.setProcessStage(stage);
            log.setProcessStatus(status);
            log.setMessage(message);
            log.setCreatedAt(LocalDateTime.now());
            processLogRepository.save(log);
        } catch (Exception e) {
            // 日志保存失败不应影响主流程
            log.error("保存处理日志失败, documentId={}, stage={}", documentId, stage, e);
        }
    }

    /**
     * 基线合并：整合多来源内容为单一文本
     * 用于 LLM 返回的节点有多个 sourceNodeIds 的情况
     *
     * @param title      节点标题
     * @param sourceTexts 多个来源的文本内容
     * @param docName    文档名称
     * @return 整合后的文本
     */
    private String integrateMultiSourceTextForBaseline(String title, List<String> sourceTexts, String docName) {
        if (sourceTexts.isEmpty()) {
            return "";
        }
        if (sourceTexts.size() == 1) {
            return sourceTexts.get(0);
        }

        StringBuilder prompt = new StringBuilder();
        prompt.append("你需要将以下内容整合成一段完整、连贯的技术文档。\n\n");
        prompt.append("节点标题: ").append(title).append("\n\n");
        prompt.append("以下内容来自文档 \"").append(docName).append("\" 的不同章节:\n\n");

        for (int i = 0; i < sourceTexts.size(); i++) {
            prompt.append("内容片段 ").append(i + 1).append(":\n").append(sourceTexts.get(i)).append("\n\n");
        }

        prompt.append("要求:\n");
        prompt.append("1. 将多个片段整合成一段完整的文档\n");
        prompt.append("2. 去除重复信息，保留核心内容\n");
        prompt.append("3. 保持逻辑连贯，上下文通顺\n");
        prompt.append("4. 保持技术文档的专业性\n");
        prompt.append("5. 输出完整的整合后内容（不是摘要）\n\n");
        prompt.append("输出格式:\n");
        prompt.append("[整合后的完整内容]");

        return llmClient.chat(prompt.toString(), null, 0.0).content();
    }

    /**
     * 汇总子节点内容生成父节点文本
     * 用于无来源节点的 text 生成
     *
     * @param childNodes 子节点列表
     * @param parentTitle 父节点标题
     * @return 汇总后的文本
     */
    private String aggregateChildNodeTexts(List<TreeNode> childNodes, String parentTitle) {
        if (childNodes == null || childNodes.isEmpty()) {
            return "";
        }

        // 收集所有子节点的信息
        StringBuilder aggregated = new StringBuilder();
        aggregated.append(parentTitle).append("包含以下内容:\n\n");

        for (TreeNode child : childNodes) {
            aggregated.append("## ").append(child.getTitle()).append("\n");
            if (child.getText() != null && !child.getText().isEmpty()) {
                aggregated.append(child.getText()).append("\n\n");
            }
        }

        // 如果子节点内容足够详细，直接使用汇总结果
        // 否则调用 LLM 进行更智能的整合
        String aggregatedText = aggregated.toString();
        if (aggregatedText.length() < 500) {
            return aggregatedText;
        }

        // 使用 LLM 生成更精炼的汇总
        StringBuilder prompt = new StringBuilder();
        prompt.append("请根据以下子模块内容，生成父模块的完整描述。\n\n");
        prompt.append("父模块名称: ").append(parentTitle).append("\n\n");
        prompt.append("子模块内容:\n").append(aggregatedText).append("\n\n");
        prompt.append("要求:\n");
        prompt.append("1. 综合所有子模块内容\n");
        prompt.append("2. 生成父模块的完整功能描述\n");
        prompt.append("3. 突出子模块之间的关系和协作\n");
        prompt.append("4. 输出完整的描述（不是摘要）\n\n");
        prompt.append("输出格式:\n");
        prompt.append("[父模块的完整描述]");

        return llmClient.chat(prompt.toString(), null, 0.0).content();
    }

    /**
     * 简化的树节点结构（用于 LLM 输入）
     */
    private static class SimplifiedTreeNode {
        public String nodeId;
        public String title;
        public String summary;
        public String prefixSummary;
        public List<SimplifiedTreeNode> children;
    }
}
