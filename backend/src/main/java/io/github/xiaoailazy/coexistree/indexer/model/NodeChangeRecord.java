package io.github.xiaoailazy.coexistree.indexer.model;

import java.time.LocalDateTime;

public class NodeChangeRecord {
    private Long docId;
    private String operation;          // 变更描述（50-100字）
    private LocalDateTime at;

    public Long getDocId() {
        return docId;
    }

    public void setDocId(Long docId) {
        this.docId = docId;
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public LocalDateTime getAt() {
        return at;
    }

    public void setAt(LocalDateTime at) {
        this.at = at;
    }
}
