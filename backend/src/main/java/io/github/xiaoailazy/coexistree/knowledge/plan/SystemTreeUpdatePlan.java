package io.github.xiaoailazy.coexistree.knowledge.plan;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * LLM 返回的系统树更新计划（需求 §11.1）。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SystemTreeUpdatePlan {

    private long changeRecordId;
    private int baseTreeVersion;
    private String changeSummary;
    private List<PlanOperation> operations = new ArrayList<>();

    public long getChangeRecordId() {
        return changeRecordId;
    }

    public void setChangeRecordId(long changeRecordId) {
        this.changeRecordId = changeRecordId;
    }

    public int getBaseTreeVersion() {
        return baseTreeVersion;
    }

    public void setBaseTreeVersion(int baseTreeVersion) {
        this.baseTreeVersion = baseTreeVersion;
    }

    public String getChangeSummary() {
        return changeSummary;
    }

    public void setChangeSummary(String changeSummary) {
        this.changeSummary = changeSummary;
    }

    public List<PlanOperation> getOperations() {
        return operations;
    }

    public void setOperations(List<PlanOperation> operations) {
        this.operations = operations != null ? operations : new ArrayList<>();
    }
}
