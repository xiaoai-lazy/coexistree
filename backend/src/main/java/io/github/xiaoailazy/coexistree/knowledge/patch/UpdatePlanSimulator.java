package io.github.xiaoailazy.coexistree.knowledge.patch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.xiaoailazy.coexistree.featuretree.model.EvidenceSource;
import io.github.xiaoailazy.coexistree.featuretree.model.FeatureChangeRef;
import io.github.xiaoailazy.coexistree.featuretree.model.FeatureCurrentState;
import io.github.xiaoailazy.coexistree.featuretree.model.FeatureNode;
import io.github.xiaoailazy.coexistree.featuretree.model.FeatureTreeNodeType;
import io.github.xiaoailazy.coexistree.featuretree.model.FeatureTreeRoot;
import io.github.xiaoailazy.coexistree.knowledge.plan.PlanOperation;
import io.github.xiaoailazy.coexistree.knowledge.plan.SystemTreeUpdatePlan;
import io.github.xiaoailazy.coexistree.shared.enums.ErrorCode;
import io.github.xiaoailazy.coexistree.shared.exception.BusinessException;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 在内存系统树上模拟更新计划（设计 §7.1、§14）；非法依赖顺序整批拒绝。
 *
 * <p><b>changeRefs 裁剪（写死策略，对齐需求 §12.3-9 / §9-7）：</b>列表下标 {@code 0} 为最旧、末尾为最新；
 * 超过 20 条时从表头 {@code remove(0)} 直至长度 ≤20。
 *
 * <p><b>evidenceSources 去重：</b>按 {@code docId} 去重，保留<strong>首次出现</strong>顺序（含合并 patch 后再整表去重）。
 */
public class UpdatePlanSimulator {

    private static final int MAX_FEATURE_CHANGE_REFS = 20;

    private final ObjectMapper objectMapper;
    private final UpdatePlanValidator planValidator;

    public UpdatePlanSimulator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.planValidator = new UpdatePlanValidator(objectMapper);
    }

    public void simulate(FeatureTreeRoot root, String planJson) throws IOException {
        planValidator.validateJsonPlan(planJson, 0L, 0L);
        SystemTreeUpdatePlan plan = objectMapper.readValue(planJson, SystemTreeUpdatePlan.class);
        FeatureNode tree = root.getTree();
        for (PlanOperation op : plan.getOperations()) {
            switch (op.getOp()) {
                case ADD_MODULE -> applyAddModule(tree, op);
                case ADD_FEATURE -> applyAddFeature(tree, op);
                case UPDATE_FEATURE -> applyUpdateFeature(tree, op);
            }
        }
        applyLimitsToAllFeatures(tree);
    }

    private void applyAddModule(FeatureNode systemRoot, PlanOperation op) throws IOException {
        FeatureNode featureRoot = requireChild(systemRoot, FeatureTreeNodeType.FEATURE_ROOT);
        FeatureNode module = objectMapper.treeToValue(op.getModule(), FeatureNode.class);
        if (module.getNodeType() != FeatureTreeNodeType.MODULE) {
            throw new BusinessException(
                    ErrorCode.UPDATE_PLAN_DEPENDENCY_FAILED, "ADD_MODULE payload must be MODULE");
        }
        featureRoot.getNodes().add(module);
    }

    private void applyAddFeature(FeatureNode systemRoot, PlanOperation op) throws IOException {
        FeatureNode featureRoot = requireChild(systemRoot, FeatureTreeNodeType.FEATURE_ROOT);
        FeatureNode module = findModule(featureRoot, op.getModuleNodeId());
        if (module == null) {
            throw new BusinessException(
                    ErrorCode.UPDATE_PLAN_DEPENDENCY_FAILED,
                    "ADD_FEATURE references unknown module: " + op.getModuleNodeId());
        }
        FeatureNode feature = objectMapper.treeToValue(op.getFeature(), FeatureNode.class);
        if (feature.getNodeType() != FeatureTreeNodeType.FEATURE) {
            throw new BusinessException(
                    ErrorCode.UPDATE_PLAN_DEPENDENCY_FAILED, "ADD_FEATURE payload must be FEATURE");
        }
        module.getNodes().add(feature);
        applyFeatureRefAndEvidenceLimits(feature);
    }

    private void applyUpdateFeature(FeatureNode systemRoot, PlanOperation op) throws IOException {
        FeatureNode target = findNodeById(systemRoot, op.getFeatureNodeId());
        if (target == null || target.getNodeType() != FeatureTreeNodeType.FEATURE) {
            throw new BusinessException(
                    ErrorCode.UPDATE_PLAN_DEPENDENCY_FAILED,
                    "UPDATE_FEATURE references unknown feature: " + op.getFeatureNodeId());
        }
        JsonNode patch = op.getPatch();
        if (patch == null) {
            return;
        }
        if (patch.hasNonNull("summary")) {
            target.setSummary(patch.get("summary").asText());
        }
        if (patch.has("currentState") && !patch.get("currentState").isNull()) {
            if (target.getCurrentState() == null) {
                target.setCurrentState(
                        objectMapper.treeToValue(patch.get("currentState"), FeatureCurrentState.class));
            } else {
                objectMapper.readerForUpdating(target.getCurrentState()).readValue(patch.get("currentState"));
            }
        }
        if (patch.has("evidenceSourcesToAdd") && patch.get("evidenceSourcesToAdd").isArray()) {
            for (JsonNode n : patch.get("evidenceSourcesToAdd")) {
                EvidenceSource add = objectMapper.treeToValue(n, EvidenceSource.class);
                target.getEvidenceSources().add(add);
            }
        }
        if (patch.has("changeRefToAdd") && !patch.get("changeRefToAdd").isNull()) {
            target.getChangeRefs()
                    .add(objectMapper.treeToValue(patch.get("changeRefToAdd"), FeatureChangeRef.class));
        }
        if (patch.has("changeRefsToAdd") && patch.get("changeRefsToAdd").isArray()) {
            for (JsonNode n : patch.get("changeRefsToAdd")) {
                target.getChangeRefs().add(objectMapper.treeToValue(n, FeatureChangeRef.class));
            }
        }
        applyFeatureRefAndEvidenceLimits(target);
    }

    /** 模拟结束后对树上全部 FEATURE 再收紧一次（应对初始 JSON 已超长等情况）。 */
    private void applyLimitsToAllFeatures(FeatureNode root) {
        Deque<FeatureNode> q = new ArrayDeque<>();
        q.add(root);
        while (!q.isEmpty()) {
            FeatureNode n = q.removeFirst();
            if (n.getNodeType() == FeatureTreeNodeType.FEATURE) {
                applyFeatureRefAndEvidenceLimits(n);
            }
            q.addAll(n.getNodes());
        }
    }

    private static void applyFeatureRefAndEvidenceLimits(FeatureNode feature) {
        capChangeRefsOldestFirst(feature.getChangeRefs());
        dedupeEvidenceSourcesByDocId(feature.getEvidenceSources());
    }

    /**
     * changeRefs：下标 0 最旧；超过 {@value #MAX_FEATURE_CHANGE_REFS} 时从头部删除。
     */
    private static void capChangeRefsOldestFirst(List<FeatureChangeRef> refs) {
        while (refs.size() > MAX_FEATURE_CHANGE_REFS) {
            refs.remove(0);
        }
    }

    /** evidenceSources：按 docId 保留首次出现；{@code docId == null} 的项不去重、全部保留。 */
    private static void dedupeEvidenceSourcesByDocId(List<EvidenceSource> sources) {
        Set<Long> seenIds = new HashSet<>();
        List<EvidenceSource> kept = new ArrayList<>();
        for (EvidenceSource s : sources) {
            Long id = s.getDocId();
            if (id == null) {
                kept.add(s);
                continue;
            }
            if (seenIds.add(id)) {
                kept.add(s);
            }
        }
        sources.clear();
        sources.addAll(kept);
    }

    private static FeatureNode requireChild(FeatureNode parent, FeatureTreeNodeType type) {
        for (FeatureNode c : parent.getNodes()) {
            if (c.getNodeType() == type) {
                return c;
            }
        }
        throw new BusinessException(
                ErrorCode.UPDATE_PLAN_DEPENDENCY_FAILED, "Tree missing child of type " + type);
    }

    private static FeatureNode findModule(FeatureNode featureRoot, String moduleNodeId) {
        if (moduleNodeId == null) {
            return null;
        }
        for (FeatureNode m : featureRoot.getNodes()) {
            if (m.getNodeType() == FeatureTreeNodeType.MODULE && moduleNodeId.equals(m.getNodeId())) {
                return m;
            }
        }
        return null;
    }

    /** DFS 按 nodeId 查找任意类型节点。 */
    private static FeatureNode findNodeById(FeatureNode root, String nodeId) {
        if (nodeId == null) {
            return null;
        }
        Deque<FeatureNode> q = new ArrayDeque<>();
        q.add(root);
        while (!q.isEmpty()) {
            FeatureNode n = q.removeFirst();
            if (nodeId.equals(n.getNodeId())) {
                return n;
            }
            q.addAll(n.getNodes());
        }
        return null;
    }
}
