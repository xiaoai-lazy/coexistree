package io.github.xiaoailazy.coexistree.featuretree.security;

import io.github.xiaoailazy.coexistree.featuretree.model.EvidenceSource;
import io.github.xiaoailazy.coexistree.featuretree.model.FeatureNode;
import io.github.xiaoailazy.coexistree.featuretree.model.FeatureTreeNodeType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FeatureSecurityLevelResolverTest {

    @Test
    void resolvesUpperBound() {
        FeatureSecurityLevelResolver r = new FeatureSecurityLevelResolver(Map.of(1L, 3, 2L, 1));
        FeatureNode f = featureWithEvidenceDocIds(1L, 2L);
        assertThat(r.resolveForFeature(f)).isEqualTo("3");
    }

    @Test
    void appliesToFeatureNode() {
        FeatureSecurityLevelResolver r = new FeatureSecurityLevelResolver(Map.of(10L, 2));
        FeatureNode f = featureWithEvidenceDocIds(10L);
        f.setSecurityLevel("99");
        r.applyToFeature(f);
        assertThat(f.getSecurityLevel()).isEqualTo("2");
    }

    @Test
    void defaultWhenNoEvidenceOrUnknownDoc() {
        FeatureSecurityLevelResolver r = new FeatureSecurityLevelResolver(Map.of());
        FeatureNode empty = new FeatureNode();
        empty.setNodeType(FeatureTreeNodeType.FEATURE);
        empty.setEvidenceSources(new ArrayList<>());
        assertThat(r.resolveForFeature(empty))
                .isEqualTo(String.valueOf(FeatureSecurityLevelResolver.DEFAULT_DOCUMENT_SECURITY_LEVEL));

        FeatureNode unknown = featureWithEvidenceDocIds(999L);
        assertThat(r.resolveForFeature(unknown))
                .isEqualTo(String.valueOf(FeatureSecurityLevelResolver.DEFAULT_DOCUMENT_SECURITY_LEVEL));
    }

    @Test
    void applyToAllFeatureNodes() {
        FeatureSecurityLevelResolver r = new FeatureSecurityLevelResolver(Map.of(1L, 5, 2L, 2));

        FeatureNode root = new FeatureNode();
        root.setNodeType(FeatureTreeNodeType.SYSTEM_ROOT);
        root.setNodes(new ArrayList<>());

        FeatureNode f1 = featureWithEvidenceDocIds(1L);
        f1.setSecurityLevel("0");
        FeatureNode f2 = featureWithEvidenceDocIds(2L);
        f2.setSecurityLevel("0");

        FeatureNode mod = new FeatureNode();
        mod.setNodeType(FeatureTreeNodeType.MODULE);
        mod.setNodes(new ArrayList<>(List.of(f1, f2)));
        root.getNodes().add(mod);

        r.applyToAllFeatureNodes(root);
        assertThat(f1.getSecurityLevel()).isEqualTo("5");
        assertThat(f2.getSecurityLevel()).isEqualTo("2");
    }

    private static FeatureNode featureWithEvidenceDocIds(Long... docIds) {
        FeatureNode f = new FeatureNode();
        f.setNodeType(FeatureTreeNodeType.FEATURE);
        List<EvidenceSource> list = new ArrayList<>();
        for (Long id : docIds) {
            EvidenceSource e = new EvidenceSource();
            e.setDocId(id);
            list.add(e);
        }
        f.setEvidenceSources(list);
        f.setChangeRefs(new ArrayList<>());
        f.setNodes(new ArrayList<>());
        return f;
    }
}
