package io.github.xiaoailazy.coexistree.knowledge.plan;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * 单条更新计划操作；各 {@code op} 的专有负载字段随 Task 8+ 使用，此处保留 JSON 透传位。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PlanOperation {

    private PlanOperationType op;
    private JsonNode module;
    private String moduleNodeId;
    private JsonNode feature;
    private String featureNodeId;
    private JsonNode patch;

    public PlanOperationType getOp() {
        return op;
    }

    public void setOp(PlanOperationType op) {
        this.op = op;
    }

    public JsonNode getModule() {
        return module;
    }

    public void setModule(JsonNode module) {
        this.module = module;
    }

    public String getModuleNodeId() {
        return moduleNodeId;
    }

    public void setModuleNodeId(String moduleNodeId) {
        this.moduleNodeId = moduleNodeId;
    }

    public JsonNode getFeature() {
        return feature;
    }

    public void setFeature(JsonNode feature) {
        this.feature = feature;
    }

    public String getFeatureNodeId() {
        return featureNodeId;
    }

    public void setFeatureNodeId(String featureNodeId) {
        this.featureNodeId = featureNodeId;
    }

    public JsonNode getPatch() {
        return patch;
    }

    public void setPatch(JsonNode patch) {
        this.patch = patch;
    }
}
