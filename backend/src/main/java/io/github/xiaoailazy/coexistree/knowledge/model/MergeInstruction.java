package io.github.xiaoailazy.coexistree.knowledge.model;

/**
 * 合并指令
 * 用于变更合并时 LLM 生成的结构化指令
 */
public class MergeInstruction {
    private String operation;        // UPDATE / CREATE / MOVE / DELETE
    private String targetNodeId;     // 目标系统树节点 ID（UPDATE/MOVE/DELETE 时必填）
    private String sourceNodeId;     // 来源文档树节点 ID（UPDATE/CREATE 时必填）
    private String newParentNodeId;  // 新父节点 ID（MOVE 时必填）

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public String getTargetNodeId() {
        return targetNodeId;
    }

    public void setTargetNodeId(String targetNodeId) {
        this.targetNodeId = targetNodeId;
    }

    public String getSourceNodeId() {
        return sourceNodeId;
    }

    public void setSourceNodeId(String sourceNodeId) {
        this.sourceNodeId = sourceNodeId;
    }

    public String getNewParentNodeId() {
        return newParentNodeId;
    }

    public void setNewParentNodeId(String newParentNodeId) {
        this.newParentNodeId = newParentNodeId;
    }
}
