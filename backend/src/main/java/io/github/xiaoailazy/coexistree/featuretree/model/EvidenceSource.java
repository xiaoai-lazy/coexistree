package io.github.xiaoailazy.coexistree.featuretree.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 功能节点证据来源（需求 §10 <code>evidenceSources</code> 元素；字段随需求扩展）。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class EvidenceSource {

    private Long docId;

    /** 文档树中的锚点节点 ID，用于 {@code document_trees} 取原文；缺省时由调用方决定是否跳过。 */
    @JsonAlias({"sourceNodeId", "anchorNodeId"})
    private String nodeId;

    public Long getDocId() {
        return docId;
    }

    public void setDocId(Long docId) {
        this.docId = docId;
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }
}
