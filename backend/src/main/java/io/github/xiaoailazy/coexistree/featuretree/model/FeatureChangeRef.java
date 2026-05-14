package io.github.xiaoailazy.coexistree.featuretree.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * <code>FEATURE.changeRefs</code> 元素占位；后续 Task 补全字段。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class FeatureChangeRef {

    private Long changeRecordId;

    public Long getChangeRecordId() {
        return changeRecordId;
    }

    public void setChangeRecordId(Long changeRecordId) {
        this.changeRecordId = changeRecordId;
    }
}
