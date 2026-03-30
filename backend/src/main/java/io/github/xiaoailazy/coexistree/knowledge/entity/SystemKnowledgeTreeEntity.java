package io.github.xiaoailazy.coexistree.knowledge.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "system_knowledge_trees")
public class SystemKnowledgeTreeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "system_id", nullable = false, unique = true)
    private Long systemId;

    @Column(name = "tree_file_path", nullable = false, length = 512)
    private String treeFilePath;

    @Column(name = "tree_version", nullable = false)
    private Integer treeVersion = 1;

    @Lob
    @Column(name = "description")
    private String description;

    @Column(name = "node_count", nullable = false)
    private Integer nodeCount = 0;

    @Column(name = "tree_status", nullable = false, length = 32)
    private String treeStatus;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getSystemId() {
        return systemId;
    }

    public void setSystemId(Long systemId) {
        this.systemId = systemId;
    }

    public String getTreeFilePath() {
        return treeFilePath;
    }

    public void setTreeFilePath(String treeFilePath) {
        this.treeFilePath = treeFilePath;
    }

    public Integer getTreeVersion() {
        return treeVersion;
    }

    public void setTreeVersion(Integer treeVersion) {
        this.treeVersion = treeVersion;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getNodeCount() {
        return nodeCount;
    }

    public void setNodeCount(Integer nodeCount) {
        this.nodeCount = nodeCount;
    }

    public String getTreeStatus() {
        return treeStatus;
    }

    public void setTreeStatus(String treeStatus) {
        this.treeStatus = treeStatus;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
