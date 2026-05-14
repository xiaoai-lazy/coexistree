package io.github.xiaoailazy.coexistree.featuretree.security;

import io.github.xiaoailazy.coexistree.document.entity.DocumentEntity;
import io.github.xiaoailazy.coexistree.featuretree.model.EvidenceSource;
import io.github.xiaoailazy.coexistree.featuretree.model.FeatureNode;
import io.github.xiaoailazy.coexistree.featuretree.model.FeatureTreeNodeType;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Objects;

/**
 * 按证据文档计算功能节点 {@code securityLevel}（需求 §7.2、§9；与 {@link DocumentEntity#getSecurityLevel()} 数值语义一致：
 * 数值越大越严格，取全部 {@code evidenceSources.docId} 对应级别的<strong>上界</strong>）。
 *
 * <p>v1 仅遍历 {@link FeatureNode#getEvidenceSources()}；{@code changeRefs} 内嵌证据在后续编排 Task 再并入。
 */
public class FeatureSecurityLevelResolver {

    /** 与 {@link DocumentEntity} 默认 {@code securityLevel = 1} 对齐。 */
    public static final int DEFAULT_DOCUMENT_SECURITY_LEVEL = 1;

    private final Map<Long, Integer> securityLevelByDocId;

    public FeatureSecurityLevelResolver(Map<Long, Integer> securityLevelByDocId) {
        this.securityLevelByDocId = Map.copyOf(Objects.requireNonNull(securityLevelByDocId));
    }

    /**
     * 计算功能节点应写入的安全级别（字符串形式，便于写入树 JSON 的 {@code securityLevel} 字段）。
     */
    public String resolveForFeature(FeatureNode feature) {
        return String.valueOf(resolveMaxNumericLevel(feature));
    }

    /**
     * 将解析结果写回 {@link FeatureNode#setSecurityLevel(String)}（覆盖 LLM 原值）。
     */
    public void applyToFeature(FeatureNode feature) {
        feature.setSecurityLevel(resolveForFeature(feature));
    }

    /** 深度优先遍历子树，对所有 {@link FeatureTreeNodeType#FEATURE} 节点执行 {@link #applyToFeature(FeatureNode)}。 */
    public void applyToAllFeatureNodes(FeatureNode root) {
        Deque<FeatureNode> stack = new ArrayDeque<>();
        stack.push(root);
        while (!stack.isEmpty()) {
            FeatureNode n = stack.pop();
            if (n.getNodeType() == FeatureTreeNodeType.FEATURE) {
                applyToFeature(n);
            }
            for (int i = n.getNodes().size() - 1; i >= 0; i--) {
                stack.push(n.getNodes().get(i));
            }
        }
    }

    private int resolveMaxNumericLevel(FeatureNode feature) {
        int max = DEFAULT_DOCUMENT_SECURITY_LEVEL;
        for (EvidenceSource es : feature.getEvidenceSources()) {
            Long docId = es.getDocId();
            if (docId == null) {
                continue;
            }
            int level = securityLevelByDocId.getOrDefault(docId, DEFAULT_DOCUMENT_SECURITY_LEVEL);
            max = Math.max(max, level);
        }
        return max;
    }
}
