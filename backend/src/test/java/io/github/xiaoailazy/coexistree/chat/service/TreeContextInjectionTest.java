package io.github.xiaoailazy.coexistree.chat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.xiaoailazy.coexistree.indexer.model.KnowledgeNodeProvenance;
import io.github.xiaoailazy.coexistree.indexer.model.NodeChangeRecord;
import io.github.xiaoailazy.coexistree.indexer.model.NodeSource;
import io.github.xiaoailazy.coexistree.indexer.model.TreeNode;
import io.github.xiaoailazy.coexistree.indexer.tree.TreeSanitizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 qa-agent 树结构注入流程的核心逻辑：
 * 加载完整树 → 去掉 text → 序列化为 JSON → 构建 prompt
 */
class TreeContextInjectionTest {

    private TreeSanitizer sanitizer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        sanitizer = new TreeSanitizer();
        objectMapper = new ObjectMapper();
    }

    @Test
    @DisplayName("完整树经过 sanitize + serialize 后，text 被移除，关键信息保留")
    void shouldStripTextButPreserveDecisionMakingFields() throws Exception {
        // 构建一个有 3 层嵌套的完整树
        TreeNode leafNode = buildNode("CE_003", "微信支付接入", 3,
                "支持微信 JSAPI、Native、H5 支付，需要申请商户号",
                "详细接入步骤：1. 申请商户号 2. 配置回调地址 3. 调用统一下单接口...",
                List.of(buildSource(1L, "doc_n_03")));

        TreeNode childNode = buildNode("CE_002", "支付功能", 2,
                "支持微信支付和支付宝两种支付方式",
                "支付流程包含：创建订单、选择支付方式、调用支付接口、回调处理",
                List.of(buildSource(1L, "doc_n_02")));
        childNode.setNodes(List.of(leafNode));

        TreeNode rootNode = buildNode("CE_001", "支付模块", 1,
                "包含支付流程、风控、对账等功能",
                "系统核心模块，负责所有支付相关功能",
                List.of(buildSource(1L, "doc_n_01")));
        rootNode.setNodes(List.of(childNode));

        // 设置 provenance（模拟真实数据）
        KnowledgeNodeProvenance prov = new KnowledgeNodeProvenance();
        prov.setCreatedAt(LocalDateTime.now());
        prov.setCreatedByDocId(1L);
        NodeChangeRecord change = new NodeChangeRecord();
        change.setDocId(1L);
        change.setOperation("基线导入");
        prov.setChangeLog(List.of(change));
        rootNode.setProvenance(prov);

        List<TreeNode> originalTree = List.of(rootNode);

        // ===== Step 1: 去掉 text 字段 =====
        List<TreeNode> sanitizedTree = sanitizer.removeText(originalTree);

        // ===== Step 2: 序列化为 JSON =====
        String treeJson = objectMapper.writeValueAsString(sanitizedTree);

        // ===== Step 3: 构建 prompt =====
        String prompt = buildQaAgentPrompt("支付功能支持哪些支付方式？", "CoExistree", treeJson);

        // ===== 验证 =====

        // 1. JSON 中不应包含任何 text 字段内容
        assertThat(treeJson).doesNotContain("详细接入步骤");
        assertThat(treeJson).doesNotContain("支付流程包含");

        // 2. 关键决策字段必须保留
        assertThat(treeJson).contains("\"nodeId\"");
        assertThat(treeJson).contains("CE_001");
        assertThat(treeJson).contains("支付模块");
        assertThat(treeJson).contains("\"summary\"");
        assertThat(treeJson).contains("包含支付流程、风控、对账");
        assertThat(treeJson).contains("\"prefixSummary\"");
        assertThat(treeJson).contains("\"sources\"");
        assertThat(treeJson).contains("\"docId\"");
        assertThat(treeJson).contains("1");  // docId
        assertThat(treeJson).contains("\"nodes\"");

        // 3. 层级结构保留
        assertThat(treeJson).contains("\"level\"");
        assertThat(treeJson).contains("3");  // leaf node level

        // 4. Prompt 包含用户问题和树结构
        assertThat(prompt).contains("支付功能支持哪些支付方式");
        assertThat(prompt).contains("CE_001");
        assertThat(prompt).contains("支付模块");
        assertThat(prompt).contains("支付功能");
        assertThat(prompt).contains("微信支付接入");

        // 5. 原始树的 text 不应被修改
        assertThat(rootNode.getText()).contains("系统核心模块");
        assertThat(leafNode.getText()).contains("详细接入步骤");

        // 6. 打印 JSON 便于确认实际格式
        String prettyJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(sanitizedTree);
        System.out.println("=== Sanitized Tree JSON (for inspection) ===");
        System.out.println(prettyJson);
        System.out.println("\n=== Token count estimate (chars) ===");
        System.out.println(treeJson.length() + " chars");
        System.out.println("\n=== Full Prompt (for inspection) ===");
        System.out.println(prompt);
    }

    @Test
    @DisplayName("多来源节点的 sources 信息完整保留")
    void shouldPreserveMultiSourceNodes() throws Exception {
        TreeNode node = buildNode("CE_005", "支付安全", 2,
                "支付安全相关配置和风控措施",
                "包含防重放攻击、签名验证、额度控制等多重安全措施",
                List.of(
                        buildSource(1L, "doc_n_05"),
                        buildSource(2L, "doc_n_12"),
                        buildSource(3L, "doc_n_20")
                ));
        node.getSources().get(0).setSecurityLevel(1);
        node.getSources().get(1).setSecurityLevel(2);

        List<TreeNode> sanitized = sanitizer.removeText(List.of(node));
        String json = objectMapper.writeValueAsString(sanitized);

        assertThat(json).contains("\"sources\"");
        assertThat(json).contains("doc_n_05");
        assertThat(json).contains("doc_n_12");
        assertThat(json).contains("doc_n_20");
        assertThat(json).contains("1");  // securityLevel
        assertThat(json).contains("2");  // securityLevel
    }

    @Test
    @DisplayName("空树不应导致注入失败")
    void shouldHandleEmptyTree() throws Exception {
        List<TreeNode> sanitized = sanitizer.removeText(List.of());
        String json = objectMapper.writeValueAsString(sanitized);

        assertThat(json).isEqualTo("[]");

        String prompt = buildQaAgentPrompt("你好", "TestSystem", json);
        assertThat(prompt).contains("[]");
    }

    // -- 辅助方法 --

    private TreeNode buildNode(String nodeId, String title, int level, String summary, String text, List<NodeSource> sources) {
        TreeNode node = new TreeNode();
        node.setNodeId(nodeId);
        node.setTitle(title);
        node.setLevel(level);
        node.setSummary(summary);
        node.setPrefixSummary("前缀摘要：" + title);
        node.setText(text);
        node.setSources(sources);
        return node;
    }

    private NodeSource buildSource(Long docId, String sourceNodeId) {
        NodeSource source = new NodeSource();
        source.setDocId(docId);
        source.setNodeId(sourceNodeId);
        return source;
    }

    private String buildQaAgentPrompt(String userQuestion, String systemName, String treeJson) {
        return """
                You are a QA Assistant for the CoExistree knowledge management system.

                Rules:
                1. Cite sources: [来源: 节点标题]
                2. Be honest — say "信息不足" if you can't find enough content
                3. Use the same language as the user's question

                ## Knowledge Tree Structure
                System: %s
                Tree:
                %s

                ## User Question
                %s

                Based on the tree structure above, identify which nodes are likely to contain the answer.
                Then use the read_node_text tool to read the actual content of those nodes.
                Finally, generate a complete answer with citations.
                """.formatted(systemName, treeJson, userQuestion);
    }
}
