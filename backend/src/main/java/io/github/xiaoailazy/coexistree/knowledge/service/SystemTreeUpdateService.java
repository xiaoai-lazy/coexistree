package io.github.xiaoailazy.coexistree.knowledge.service;

/**
 * 系统树按变更批次应用（设计 §6 {@code applyChange} 编排入口）。
 */
public interface SystemTreeUpdateService {

    void applyChange(long changeRecordId);
}
