package io.github.xiaoailazy.coexistree.knowledge.service;

import io.github.xiaoailazy.coexistree.shared.util.JsonUtils;
import io.github.xiaoailazy.coexistree.knowledge.entity.SystemTreeSnapshotEntity;
import io.github.xiaoailazy.coexistree.knowledge.model.SystemKnowledgeTree;
import io.github.xiaoailazy.coexistree.knowledge.repository.SystemTreeSnapshotRepository;
import io.github.xiaoailazy.coexistree.knowledge.storage.SystemTreeFileLoader;
import io.github.xiaoailazy.coexistree.indexer.model.TreeNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 系统树快照服务
 */
@Slf4j
@Service
public class SnapshotService {

    private static final DateTimeFormatter SNAPSHOT_NAME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm");

    private final SystemTreeSnapshotRepository snapshotRepository;
    private final SystemTreeFileLoader treeFileLoader;
    private final JsonUtils jsonUtils;

    public SnapshotService(SystemTreeSnapshotRepository snapshotRepository,
                       SystemTreeFileLoader treeFileLoader,
                       JsonUtils jsonUtils) {
        this.snapshotRepository = snapshotRepository;
        this.treeFileLoader = treeFileLoader;
        this.jsonUtils = jsonUtils;
    }

    /**
     * 创建快照
     *
     * @param systemTree      系统树
     * @param triggeredByDocId 触发快照的文档 ID
     * @return 创建的快照实体
     */
    public SystemTreeSnapshotEntity createSnapshot(SystemKnowledgeTree systemTree, Long triggeredByDocId) {
        LocalDateTime now = LocalDateTime.now();
        String snapshotName = generateSnapshotName(now);

        // 统计节点数量
        int nodeCount = countNodes(systemTree.getStructure());

        SystemTreeSnapshotEntity snapshot = SystemTreeSnapshotEntity.builder()
                .systemId(systemTree.getSystemId())
                .snapshotName(snapshotName)
                .treeJson(jsonUtils.toJson(systemTree))
                .triggeredByDocId(triggeredByDocId)
                .triggeredBy("SYSTEM")
                .nodeCount(nodeCount)
                .isPinned(false)
                .status("ACTIVE")
                .createdAt(now)
                .build();

        snapshotRepository.save(snapshot);
        log.info("创建系统树快照成功, systemId={}, snapshotName={}, nodeCount={}",
                systemTree.getSystemId(), snapshotName, nodeCount);

        return snapshot;
    }

    /**
     * 生成快照名称
     */
    private String generateSnapshotName(LocalDateTime dateTime) {
        return "tree-" + dateTime.format(SNAPSHOT_NAME_FORMAT);
    }

    /**
     * 统计节点总数（递归）
     */
    private int countNodes(List<TreeNode> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return 0;
        }
        int count = nodes.size();
        for (TreeNode node : nodes) {
            if (node.getNodes() != null) {
                count += countNodes(node.getNodes());
            }
        }
        return count;
    }

    /**
     * 获取指定系统的可用快照列表（按时间倒序）
     *
     * @param systemId 系统 ID
     * @return 快照列表
     */
    public List<SnapshotItem> getAvailableSnapshots(Long systemId) {
        List<SystemTreeSnapshotEntity> snapshots = snapshotRepository.findBySystemIdOrderByCreatedAtDesc(systemId);
        return snapshots.stream()
                .filter(s -> "ACTIVE".equals(s.getStatus()))
                .map(this::toSnapshotItem)
                .collect(Collectors.toList());
    }

    private SnapshotItem toSnapshotItem(SystemTreeSnapshotEntity snapshot) {
        return new SnapshotItem(
                snapshot.getSnapshotName(),
                snapshot.getCreatedAt(),
                snapshot.getTriggeredBy(),
                snapshot.getNodeCount(),
                snapshot.getIsPinned()
        );
    }

    /**
     * 快照项（用于返回给前端）
     */
    public static class SnapshotItem {
        private final String snapshotName;
        private final LocalDateTime createdAt;
        private final String triggeredBy;
        private final Integer nodeCount;
        private final Boolean isPinned;

        public SnapshotItem(String snapshotName, LocalDateTime createdAt, String triggeredBy,
                           Integer nodeCount, Boolean isPinned) {
            this.snapshotName = snapshotName;
            this.createdAt = createdAt;
            this.triggeredBy = triggeredBy;
            this.nodeCount = nodeCount;
            this.isPinned = isPinned;
        }

        public String getSnapshotName() {
            return snapshotName;
        }

        public LocalDateTime getCreatedAt() {
            return createdAt;
        }

        public String getTriggeredBy() {
            return triggeredBy;
        }

        public Integer getNodeCount() {
            return nodeCount;
        }

        public Boolean getIsPinned() {
            return isPinned;
        }
    }
}
