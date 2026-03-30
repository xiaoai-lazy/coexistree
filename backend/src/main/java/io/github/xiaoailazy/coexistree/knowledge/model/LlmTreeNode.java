package io.github.xiaoailazy.coexistree.knowledge.model;

import java.util.ArrayList;
import java.util.List;

/**
 * LLM 输出的树节点结构
 * 用于基线合并时 LLM 生成系统架构
 */
public class LlmTreeNode {
    private String title;
    private Integer level;              // 层级 1-5
    private List<String> sourceNodeIds = new ArrayList<>();
    private List<LlmTreeNode> children = new ArrayList<>();

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Integer getLevel() {
        return level;
    }

    public void setLevel(Integer level) {
        this.level = level;
    }

    public List<String> getSourceNodeIds() {
        return sourceNodeIds;
    }

    public void setSourceNodeIds(List<String> sourceNodeIds) {
        this.sourceNodeIds = sourceNodeIds;
    }

    public List<LlmTreeNode> getChildren() {
        return children;
    }

    public void setChildren(List<LlmTreeNode> children) {
        this.children = children;
    }
}
