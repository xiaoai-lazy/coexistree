package io.github.xiaoailazy.coexistree.knowledge.plan;

/**
 * 系统树更新计划支持的算子（需求 §11；v1 不包含 {@code MOVE_FEATURE}）。
 */
public enum PlanOperationType {
    ADD_MODULE,
    ADD_FEATURE,
    UPDATE_FEATURE
}
