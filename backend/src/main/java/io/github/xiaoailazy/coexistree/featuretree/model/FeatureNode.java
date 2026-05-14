package io.github.xiaoailazy.coexistree.featuretree.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 功能树节点（需求 §10.1）；递归 <code>nodes</code>。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class FeatureNode {

    private String nodeId;
    private FeatureTreeNodeType nodeType;
    private String title;
    private String summary;
    private FeatureCurrentState currentState;
    private List<EvidenceSource> evidenceSources = new ArrayList<>();
    private List<FeatureChangeRef> changeRefs = new ArrayList<>();
    private List<FeatureNode> nodes = new ArrayList<>();
    private String securityLevel;

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public FeatureTreeNodeType getNodeType() {
        return nodeType;
    }

    /** 与计划文案「type」一致，序列化字段仍为 {@code nodeType}。 */
    public FeatureTreeNodeType getType() {
        return nodeType;
    }

    public void setNodeType(FeatureTreeNodeType nodeType) {
        this.nodeType = nodeType;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public FeatureCurrentState getCurrentState() {
        return currentState;
    }

    public void setCurrentState(FeatureCurrentState currentState) {
        this.currentState = currentState;
    }

    public List<EvidenceSource> getEvidenceSources() {
        return evidenceSources;
    }

    public void setEvidenceSources(List<EvidenceSource> evidenceSources) {
        this.evidenceSources =
                evidenceSources != null ? evidenceSources : new ArrayList<>();
    }

    public List<FeatureChangeRef> getChangeRefs() {
        return changeRefs;
    }

    public void setChangeRefs(List<FeatureChangeRef> changeRefs) {
        this.changeRefs = changeRefs != null ? changeRefs : new ArrayList<>();
    }

    public List<FeatureNode> getNodes() {
        return nodes;
    }

    public void setNodes(List<FeatureNode> nodes) {
        this.nodes = nodes != null ? nodes : new ArrayList<>();
    }

    public String getSecurityLevel() {
        return securityLevel;
    }

    public void setSecurityLevel(String securityLevel) {
        this.securityLevel = securityLevel;
    }
}
