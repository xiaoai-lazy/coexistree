package io.github.xiaoailazy.coexistree.indexer.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class KnowledgeNodeProvenance {
    private Long createdByDocId;
    private LocalDateTime createdAt;
    private Long lastUpdatedByDocId;
    private LocalDateTime lastUpdatedAt;
    private List<NodeChangeRecord> changeLog = new ArrayList<>();

    public Long getCreatedByDocId() {
        return createdByDocId;
    }

    public void setCreatedByDocId(Long createdByDocId) {
        this.createdByDocId = createdByDocId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Long getLastUpdatedByDocId() {
        return lastUpdatedByDocId;
    }

    public void setLastUpdatedByDocId(Long lastUpdatedByDocId) {
        this.lastUpdatedByDocId = lastUpdatedByDocId;
    }

    public LocalDateTime getLastUpdatedAt() {
        return lastUpdatedAt;
    }

    public void setLastUpdatedAt(LocalDateTime lastUpdatedAt) {
        this.lastUpdatedAt = lastUpdatedAt;
    }

    public List<NodeChangeRecord> getChangeLog() {
        return changeLog;
    }

    public void setChangeLog(List<NodeChangeRecord> changeLog) {
        this.changeLog = changeLog;
    }
}
