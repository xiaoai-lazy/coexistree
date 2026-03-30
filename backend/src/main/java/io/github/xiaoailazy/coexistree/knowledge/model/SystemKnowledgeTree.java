package io.github.xiaoailazy.coexistree.knowledge.model;

import io.github.xiaoailazy.coexistree.indexer.model.TreeNode;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class SystemKnowledgeTree {
    private Long systemId;
    private String systemCode;
    private String systemName;
    private Integer treeVersion;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime lastUpdatedAt;
    private List<TreeNode> structure = new ArrayList<>();

    public Long getSystemId() {
        return systemId;
    }

    public void setSystemId(Long systemId) {
        this.systemId = systemId;
    }

    public String getSystemCode() {
        return systemCode;
    }

    public void setSystemCode(String systemCode) {
        this.systemCode = systemCode;
    }

    public String getSystemName() {
        return systemName;
    }

    public void setSystemName(String systemName) {
        this.systemName = systemName;
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getLastUpdatedAt() {
        return lastUpdatedAt;
    }

    public void setLastUpdatedAt(LocalDateTime lastUpdatedAt) {
        this.lastUpdatedAt = lastUpdatedAt;
    }

    public List<TreeNode> getStructure() {
        return structure;
    }

    public void setStructure(List<TreeNode> structure) {
        this.structure = structure;
    }
}
