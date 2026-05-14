package io.github.xiaoailazy.coexistree.agent.context;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.adk.agents.CallbackContext;
import com.google.adk.agents.Callbacks;
import com.google.adk.models.LlmRequest;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import io.github.xiaoailazy.coexistree.indexer.model.TreeNode;
import io.github.xiaoailazy.coexistree.indexer.tree.TreeSanitizer;
import io.github.xiaoailazy.coexistree.knowledge.model.SystemKnowledgeTree;
import io.github.xiaoailazy.coexistree.knowledge.service.SystemKnowledgeTreeService;
import io.reactivex.rxjava3.core.Maybe;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * qa-agent 的树上下文加载器。
 *
 * 在 qa-agent 第一次 LLM 调用前，自动加载系统知识树结构（去掉 text 字段），
 * 并注入到 LLM 请求的上下文中，使 agent 能看到完整的树结构做语义判断。
 */
@Slf4j
@Component
public class TreeContextLoader {

    private static final String TREE_INJECTED_KEY = "user:treeInjected";

    private final SystemKnowledgeTreeService treeService;
    private final TreeSanitizer treeSanitizer;
    private final ObjectMapper objectMapper;

    public TreeContextLoader(
            SystemKnowledgeTreeService treeService,
            TreeSanitizer treeSanitizer,
            ObjectMapper objectMapper
    ) {
        this.treeService = treeService;
        this.treeSanitizer = treeSanitizer;
        this.objectMapper = objectMapper;
    }

    /**
     * 创建 beforeModelCallback，在 qa-agent 每次 LLM 调用前执行。
     * 只在第一次调用时注入树上下文，后续调用跳过。
     */
    public Callbacks.BeforeModelCallback createBeforeModelCallback() {
        return (ctx, builder) -> {
            try {
                Map<String, Object> state = ctx.state();
                Object alreadyInjected = state.get(TREE_INJECTED_KEY);

                if (alreadyInjected != null) {
                    log.debug("[treeContext] already injected for agent={}, skipping", ctx.agentName());
                    return Maybe.empty();
                }

                // 获取 systemId
                Long systemId = toLong(state.get("user:systemId"));
                if (systemId == null) {
                    log.warn("[treeContext] user:systemId not found in session state, cannot inject tree context");
                    return Maybe.empty();
                }

                log.info("[treeContext] loading tree for systemId={}, agent={}", systemId, ctx.agentName());

                // 加载知识树
                SystemKnowledgeTree tree = treeService.getActiveTree(systemId);
                List<TreeNode> structure = tree.getStructure();

                if (structure == null || structure.isEmpty()) {
                    log.warn("[treeContext] tree structure is empty for systemId={}", systemId);
                    state.put(TREE_INJECTED_KEY, true);
                    return Maybe.empty();
                }

                // 去掉 text 字段，保留 title + summary + sources + 层级
                List<TreeNode> sanitized = treeSanitizer.removeText(structure);

                // 序列化为 JSON
                String treeJson = objectMapper.writeValueAsString(sanitized);
                log.info("[treeContext] tree loaded, nodeId count={}, jsonLen={}",
                        countNodes(sanitized), treeJson.length());

                // 构建上下文消息
                String contextText = buildTreeContextMessage(tree, treeJson);

                // 注入到 LLM 请求的 contents 中，作为第一条 system 消息
                List<Content> newContents = new ArrayList<>();
                newContents.add(Content.fromParts(Part.fromText(contextText)));

                // 追加原有 contents
                LlmRequest current = builder.build();
                if (current.contents() != null) {
                    newContents.addAll(current.contents());
                }

                // 重新设置 builder 的 contents
                builder.contents(newContents);

                // 打印完整的提示词原文
                StringBuilder fullPrompt = new StringBuilder();
                for (Content c : newContents) {
                    if (c.text() != null) {
                        fullPrompt.append(c.text()).append("\n\n");
                    }
                }
                log.info("[treeContext] === FULL PROMPT AFTER INJECTION ===\n{}\n=========================================", fullPrompt);

                // 标记已注入
                state.put(TREE_INJECTED_KEY, true);

                log.info("[treeContext] injected successfully, totalContentLen={}",
                        newContents.stream().mapToInt(c -> c.text() != null ? c.text().length() : 0).sum());

            } catch (Exception e) {
                log.error("[treeContext] failed to inject tree context, agent={}", ctx.agentName(), e);
            }
            return Maybe.empty();
        };
    }

    /**
     * 构建树上下文消息，作为 system 级别的前缀注入。
     */
    private String buildTreeContextMessage(SystemKnowledgeTree tree, String treeJson) {
        return """
                You are working with the knowledge tree of system "%s" (code: %s, version: %d).

                The tree structure below contains nodes with: nodeId, title, summary, nodeType, currentState, evidenceSources (docId + nodeId for document_trees), and legacy sources.
                The text field has been removed to save context. Use the read_node_text tool with docId and nodeId from evidenceSources (or sources) to read the actual content.

                ## Knowledge Tree Structure
                %s

                Based on this tree structure, you will identify which nodes are relevant to the user's question,
                then use read_node_text to read their full content, and finally generate answers with citations.
                """.formatted(
                tree.getSystemName(),
                tree.getSystemCode(),
                tree.getTreeVersion(),
                treeJson
        );
    }

    private int countNodes(List<TreeNode> nodes) {
        int count = 0;
        for (TreeNode node : nodes) {
            count++;
            if (node.getNodes() != null && !node.getNodes().isEmpty()) {
                count += countNodes(node.getNodes());
            }
        }
        return count;
    }

    private static Long toLong(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Number n) return n.longValue();
        return Long.parseLong(obj.toString());
    }
}
