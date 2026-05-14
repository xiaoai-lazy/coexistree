package io.github.xiaoailazy.coexistree.document.tree;

/**
 * 按变更记录为范围内文档构建 {@code document_trees}（设计 §3.1、§6.A-3；仅 REQUIREMENT/DESIGN）。
 */
public interface DocumentTreeBuildService {

    void buildDocumentTreesForChange(Long changeRecordId);
}
