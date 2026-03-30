package io.github.xiaoailazy.coexistree.indexer.model;

import java.util.ArrayList;
import java.util.List;

public class TreeNode {
    private String nodeId;
    private String title;
    private Integer lineNum;
    private Integer level;
    private String text;
    private String summary;
    private String prefixSummary;
    private List<TreeNode> nodes = new ArrayList<>();
    private List<NodeSource> sources;
    private KnowledgeNodeProvenance provenance;

    public String getNodeId() { return nodeId; }
    public void setNodeId(String nodeId) { this.nodeId = nodeId; }
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
    public List<TreeNode> getNodes() { return nodes; }
    public void setNodes(List<TreeNode> nodes) { this.nodes = nodes; }
    public List<NodeSource> getSources() { return sources; }
    public void setSources(List<NodeSource> sources) { this.sources = sources; }
    public KnowledgeNodeProvenance getProvenance() { return provenance; }
    public void setProvenance(KnowledgeNodeProvenance provenance) { this.provenance = provenance; }
}

