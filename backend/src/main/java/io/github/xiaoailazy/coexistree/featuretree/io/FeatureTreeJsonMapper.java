package io.github.xiaoailazy.coexistree.featuretree.io;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.xiaoailazy.coexistree.featuretree.model.FeatureNode;
import io.github.xiaoailazy.coexistree.featuretree.model.FeatureTreeRoot;

import java.io.IOException;
import java.util.Objects;

/**
 * 功能树 JSON 与 POJO 互转（设计 §3.2 选项 a）。
 */
public class FeatureTreeJsonMapper {

    private final ObjectMapper objectMapper;

    public FeatureTreeJsonMapper(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    /**
     * 解析初始系统树 JSON：支持带 <code>systemSummary</code> + <code>tree</code> 信封，或直接为根节点对象。
     */
    public FeatureTreeRoot parseRoot(String json) throws IOException {
        JsonNode root = objectMapper.readTree(json);
        if (root.hasNonNull("tree")) {
            String summary = root.path("systemSummary").asText(null);
            FeatureNode tree = objectMapper.treeToValue(root.get("tree"), FeatureNode.class);
            return new FeatureTreeRoot(summary, tree);
        }
        FeatureNode tree = objectMapper.treeToValue(root, FeatureNode.class);
        return new FeatureTreeRoot(null, tree);
    }

    public String writeTree(FeatureTreeRoot root) throws IOException {
        if (root.getSystemSummary() != null) {
            return objectMapper.writeValueAsString(
                    objectMapper.createObjectNode()
                            .put("systemSummary", root.getSystemSummary())
                            .set("tree", objectMapper.valueToTree(root.getTree())));
        }
        return objectMapper.writeValueAsString(root.getTree());
    }

    public JsonNode toJsonNode(FeatureTreeRoot root) throws IOException {
        return objectMapper.readTree(writeTree(root));
    }
}
