package io.github.xiaoailazy.coexistree.indexer.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.github.xiaoailazy.coexistree.featuretree.model.EvidenceSource;
import io.github.xiaoailazy.coexistree.featuretree.model.FeatureCurrentState;

import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TreeNode {
    private String nodeId;
    /** 功能树节点类型（如 SYSTEM_ROOT、FEATURE_ROOT、FEATURE、MODULE），可选。 */
    private String nodeType;
    private String title;
    private Integer lineNum;
    private Integer level;
    private String text;
    private String summary;
    private String prefixSummary;
    private FeatureCurrentState currentState;
    private List<EvidenceSource> evidenceSources;
    private List<TreeNode> nodes = new ArrayList<>();
    private List<NodeSource> sources;
    private KnowledgeNodeProvenance provenance;

    public String getNodeId() { return nodeId; }
    public void setNodeId(String nodeId) { this.nodeId = nodeId; }

    public String getNodeType() {
        return nodeType;
    }

    public void setNodeType(String nodeType) {
        this.nodeType = nodeType;
    }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public Integer getLineNum() { return lineNum; }
    public void setLineNum(Integer lineNum) { this.lineNum = lineNum; }
    public Integer getLevel() { return level; }
    public void setLevel(Integer level) { this.level = level; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getPrefixSummary() { return prefixSummary; }
    public void setPrefixSummary(String prefixSummary) { this.prefixSummary = prefixSummary; }

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
        this.evidenceSources = evidenceSources;
    }

    public List<TreeNode> getNodes() { return nodes; }
    public void setNodes(List<TreeNode> nodes) { this.nodes = nodes; }
    public List<NodeSource> getSources() { return sources; }
    public void setSources(List<NodeSource> sources) { this.sources = sources; }
    public KnowledgeNodeProvenance getProvenance() { return provenance; }
    public void setProvenance(KnowledgeNodeProvenance provenance) { this.provenance = provenance; }
}

