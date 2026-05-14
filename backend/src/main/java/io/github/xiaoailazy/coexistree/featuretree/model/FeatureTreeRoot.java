package io.github.xiaoailazy.coexistree.featuretree.model;

/**
 * 初始系统树 JSON 中的根树对象（需求 §10.1 的 <code>tree</code>），由 {@code FeatureTreeJsonMapper#parseRoot} 返回。
 */
public class FeatureTreeRoot {

    private final String systemSummary;
    private final FeatureNode tree;

    public FeatureTreeRoot(String systemSummary, FeatureNode tree) {
        this.systemSummary = systemSummary;
        this.tree = tree;
    }

    public String getSystemSummary() {
        return systemSummary;
    }

    public FeatureNode getTree() {
        return tree;
    }

    /** 根节点类型（<code>tree.nodeType</code>）。 */
    public FeatureTreeNodeType getType() {
        return tree != null ? tree.getNodeType() : null;
    }
}
