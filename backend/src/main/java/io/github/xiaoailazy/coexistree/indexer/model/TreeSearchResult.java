package io.github.xiaoailazy.coexistree.indexer.model;

import java.util.List;

public class TreeSearchResult {
    private String responseId;
    private String thinking;
    private List<String> nodeList;

    public TreeSearchResult() {}

    public TreeSearchResult(String responseId, String thinking, List<String> nodeList) {
        this.responseId = responseId;
        this.thinking = thinking;
        this.nodeList = nodeList;
    }

    public String getResponseId() { return responseId; }
    public void setResponseId(String responseId) { this.responseId = responseId; }

    public String getThinking() { return thinking; }
    public void setThinking(String thinking) { this.thinking = thinking; }

    public List<String> getNodeList() { return nodeList; }
    public void setNodeList(List<String> nodeList) { this.nodeList = nodeList; }
}
