package io.github.xiaoailazy.coexistree.chat.service.impl;

import io.github.xiaoailazy.coexistree.featuretree.model.FeatureCurrentState;
import io.github.xiaoailazy.coexistree.indexer.model.TreeNode;
import io.github.xiaoailazy.coexistree.indexer.model.TreeSearchResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TreeSearchServiceImplTest {

    private final TreeSearchServiceImpl service = new TreeSearchServiceImpl();

    @Test
    void skipsStructuralRootsButSearchesDescendants() {
        TreeNode systemRoot = new TreeNode();
        systemRoot.setNodeId("sys");
        systemRoot.setNodeType("SYSTEM_ROOT");
        systemRoot.setTitle("mega keyword");
        systemRoot.setSummary("");

        TreeNode featureRoot = new TreeNode();
        featureRoot.setNodeId("fr");
        featureRoot.setNodeType("FEATURE_ROOT");
        featureRoot.setTitle("another mega keyword");
        featureRoot.setSummary("");

        TreeNode feature = new TreeNode();
        feature.setNodeId("f1");
        feature.setNodeType("FEATURE");
        feature.setTitle("支付");
        feature.setSummary("摘要");
        FeatureCurrentState cs = new FeatureCurrentState();
        cs.setRequirementSummary("mega keyword in state");
        feature.setCurrentState(cs);

        featureRoot.setNodes(List.of(feature));
        systemRoot.setNodes(List.of(featureRoot));

        TreeSearchResult r = service.search(List.of(systemRoot), "mega keyword", null);
        assertThat(r.getNodeList()).containsExactly("f1");
    }

    @Test
    void featureNodeDoesNotMatchOnTextAlone() {
        TreeNode n = new TreeNode();
        n.setNodeId("x");
        n.setNodeType("FEATURE");
        n.setTitle("t");
        n.setSummary("s");
        n.setText("secret-in-text-only");

        TreeSearchResult r = service.search(List.of(n), "secret-in-text", null);
        assertThat(r.getNodeList()).isEmpty();
    }
}
