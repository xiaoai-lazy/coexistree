package io.github.xiaoailazy.coexistree.knowledge.patch;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.xiaoailazy.coexistree.featuretree.model.EvidenceSource;
import io.github.xiaoailazy.coexistree.featuretree.model.FeatureChangeRef;
import io.github.xiaoailazy.coexistree.featuretree.model.FeatureCurrentState;
import io.github.xiaoailazy.coexistree.featuretree.model.FeatureNode;
import io.github.xiaoailazy.coexistree.featuretree.model.FeatureTreeNodeType;
import io.github.xiaoailazy.coexistree.featuretree.model.FeatureTreeRoot;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UpdatePlanSimulatorChangeRefsCapTest {

    @Test
    void trimsChangeRefsTo20OldestDropped() throws Exception {
        FeatureTreeRoot root = treeWithFeatureHavingChangeRefs(19);
        String planJson =
                """
                {
                  "changeRecordId": 1,
                  "baseTreeVersion": 1,
                  "operations": [
                    {
                      "op": "UPDATE_FEATURE",
                      "featureNodeId": "f1",
                      "patch": {
                        "changeRefsToAdd": [
                          { "changeRecordId": 100 },
                          { "changeRecordId": 101 }
                        ]
                      }
                    }
                  ]
                }""";

        new UpdatePlanSimulator(new ObjectMapper()).simulate(root, planJson);

        FeatureNode f = findFeature(root.getTree(), "f1");
        assertThat(f.getChangeRefs()).hasSize(20);
        assertThat(f.getChangeRefs().get(0).getChangeRecordId()).isEqualTo(2L);
        assertThat(f.getChangeRefs().get(18).getChangeRecordId()).isEqualTo(100L);
        assertThat(f.getChangeRefs().get(19).getChangeRecordId()).isEqualTo(101L);
    }

    @Test
    void dedupesEvidenceSourcesByDocIdKeepingFirst() throws Exception {
        FeatureTreeRoot root = treeWithFeatureHavingEvidence(1L);
        String planJson =
                """
                {
                  "changeRecordId": 1,
                  "baseTreeVersion": 1,
                  "operations": [
                    {
                      "op": "UPDATE_FEATURE",
                      "featureNodeId": "f1",
                      "patch": {
                        "evidenceSourcesToAdd": [
                          { "docId": 1, "nodeId": "dup" },
                          { "docId": 2, "nodeId": "ok" }
                        ]
                      }
                    }
                  ]
                }""";

        new UpdatePlanSimulator(new ObjectMapper()).simulate(root, planJson);

        FeatureNode f = findFeature(root.getTree(), "f1");
        assertThat(f.getEvidenceSources()).hasSize(2);
        assertThat(f.getEvidenceSources().get(0).getDocId()).isEqualTo(1L);
        assertThat(f.getEvidenceSources().get(1).getDocId()).isEqualTo(2L);
    }

    private static FeatureTreeRoot treeWithFeatureHavingChangeRefs(int initialRefCount) {
        FeatureNode system = node("sys", FeatureTreeNodeType.SYSTEM_ROOT, "S", "s");
        FeatureNode fr = node("fr", FeatureTreeNodeType.FEATURE_ROOT, "F", "f");
        FeatureNode mod = node("mod1", FeatureTreeNodeType.MODULE, "M", "m");
        FeatureNode feat = node("f1", FeatureTreeNodeType.FEATURE, "feat", "sum");
        FeatureCurrentState cs = new FeatureCurrentState();
        cs.setConfidence("HIGH");
        cs.setStatus("ACTIVE");
        feat.setCurrentState(cs);
        feat.setEvidenceSources(new ArrayList<>());
        List<FeatureChangeRef> refs = new ArrayList<>();
        for (long i = 1; i <= initialRefCount; i++) {
            FeatureChangeRef r = new FeatureChangeRef();
            r.setChangeRecordId(i);
            refs.add(r);
        }
        feat.setChangeRefs(refs);
        mod.getNodes().add(feat);
        fr.getNodes().add(mod);
        system.getNodes().add(fr);
        return new FeatureTreeRoot(null, system);
    }

    private static FeatureTreeRoot treeWithFeatureHavingEvidence(long existingDocId) {
        FeatureNode system = node("sys", FeatureTreeNodeType.SYSTEM_ROOT, "S", "s");
        FeatureNode fr = node("fr", FeatureTreeNodeType.FEATURE_ROOT, "F", "f");
        FeatureNode mod = node("mod1", FeatureTreeNodeType.MODULE, "M", "m");
        FeatureNode feat = node("f1", FeatureTreeNodeType.FEATURE, "feat", "sum");
        FeatureCurrentState cs = new FeatureCurrentState();
        cs.setConfidence("HIGH");
        cs.setStatus("ACTIVE");
        feat.setCurrentState(cs);
        List<EvidenceSource> ev = new ArrayList<>();
        EvidenceSource first = new EvidenceSource();
        first.setDocId(existingDocId);
        ev.add(first);
        feat.setEvidenceSources(ev);
        feat.setChangeRefs(new ArrayList<>());
        mod.getNodes().add(feat);
        fr.getNodes().add(mod);
        system.getNodes().add(fr);
        return new FeatureTreeRoot(null, system);
    }

    private static FeatureNode node(
            String id, FeatureTreeNodeType type, String title, String summary) {
        FeatureNode n = new FeatureNode();
        n.setNodeId(id);
        n.setNodeType(type);
        n.setTitle(title);
        n.setSummary(summary);
        n.setNodes(new ArrayList<>());
        return n;
    }

    private static FeatureNode findFeature(FeatureNode root, String featureNodeId) {
        List<FeatureNode> stack = new ArrayList<>();
        stack.add(root);
        while (!stack.isEmpty()) {
            FeatureNode n = stack.remove(stack.size() - 1);
            if (featureNodeId.equals(n.getNodeId()) && n.getNodeType() == FeatureTreeNodeType.FEATURE) {
                return n;
            }
            stack.addAll(n.getNodes());
        }
        throw new IllegalStateException("feature not found: " + featureNodeId);
    }
}
