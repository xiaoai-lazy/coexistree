package io.github.xiaoailazy.coexistree.featuretree.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * <code>FEATURE.currentState</code>（需求 §10.1）；非 FEATURE 节点可为 null。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class FeatureCurrentState {

    private String requirementSummary;
    private String designSummary;
    private String implementationLogic;
    private String constraints;
    private String latestChangeSummary;
    private String status;
    private String confidence;
    private String updatedAt;

    public String getRequirementSummary() {
        return requirementSummary;
    }

    public void setRequirementSummary(String requirementSummary) {
        this.requirementSummary = requirementSummary;
    }

    public String getDesignSummary() {
        return designSummary;
    }

    public void setDesignSummary(String designSummary) {
        this.designSummary = designSummary;
    }

    public String getImplementationLogic() {
        return implementationLogic;
    }

    public void setImplementationLogic(String implementationLogic) {
        this.implementationLogic = implementationLogic;
    }

    public String getConstraints() {
        return constraints;
    }

    public void setConstraints(String constraints) {
        this.constraints = constraints;
    }

    public String getLatestChangeSummary() {
        return latestChangeSummary;
    }

    public void setLatestChangeSummary(String latestChangeSummary) {
        this.latestChangeSummary = latestChangeSummary;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getConfidence() {
        return confidence;
    }

    public void setConfidence(String confidence) {
        this.confidence = confidence;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }
}
