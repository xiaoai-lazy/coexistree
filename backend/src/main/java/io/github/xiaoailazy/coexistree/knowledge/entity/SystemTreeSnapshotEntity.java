package io.github.xiaoailazy.coexistree.knowledge.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 系统树快照实体
 */
@Entity
@Table(name = "system_tree_snapshots")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemTreeSnapshotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 系统 ID
     */
    @Column(name = "system_id", nullable = false)
    private Long systemId;

    /**
     * 快照名称，格式: tree-YYYY-MM-DD-HH-mm
     */
    @Column(name = "snapshot_name", nullable = false, length = 50)
    private String snapshotName;

    /**
     * 树 JSON 数据
     */
    @Column(name = "tree_json", nullable = false, columnDefinition = "TEXT")
    private String treeJson;

    /**
     * 触发快照的文档 ID
     */
    @Column(name = "triggered_by_doc_id")
    private Long triggeredByDocId;

    /**
     * 操作人
     */
    @Column(name = "triggered_by", length = 100)
    private String triggeredBy;

    /**
     * 节点数量
     */
    @Column(name = "node_count")
    private Integer nodeCount;

    /**
     * 是否固定（不自动删除）
     */
    @Builder.Default
    @Column(name = "is_pinned")
    private Boolean isPinned = false;

    /**
     * 状态: ACTIVE, DELETED
     */
    @Builder.Default
    @Column(name = "status", length = 20)
    private String status = "ACTIVE";

    /**
     * 创建时间
     */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /**
     * 删除时间
     */
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}
