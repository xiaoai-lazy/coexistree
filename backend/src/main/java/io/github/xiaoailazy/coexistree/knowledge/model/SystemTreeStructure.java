package io.github.xiaoailazy.coexistree.knowledge.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 系统树结构（LLM 输出）
 * 用于基线合并时接收 LLM 生成的系统架构
 */
public class SystemTreeStructure {
    private String systemDescription;
    private List<LlmTreeNode> structure = new ArrayList<>();

    public String getSystemDescription() {
        return systemDescription;
    }

    public void setSystemDescription(String systemDescription) {
        this.systemDescription = systemDescription;
    }

    public List<LlmTreeNode> getStructure() {
        return structure;
    }

    public void setStructure(List<LlmTreeNode> structure) {
        this.structure = structure;
    }
}
