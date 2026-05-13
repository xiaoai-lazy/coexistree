package io.github.xiaoailazy.coexistree.agent.tools;

import com.google.adk.tools.ToolContext;
import io.github.xiaoailazy.coexistree.document.service.DocumentTreeService;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * 批量读取知识树节点的原文。
 */
@Slf4j
public class ReadNodeTextTool {

    private final DocumentTreeService documentTreeService;

    public ReadNodeTextTool(DocumentTreeService documentTreeService) {
        this.documentTreeService = documentTreeService;
    }

    /**
     * 节点引用，用于批量读取原文。
     */
    public record NodeRef(long docId, String nodeId) {}

    /**
     * 批量读取多个节点的原文。
     *
     * @param nodes 节点列表，每个节点包含 docId 和 nodeId
     */
    public String readNodeTexts(List<NodeRef> nodes, ToolContext toolContext) {
        try {
            log.info("[tool][readNodeTexts] start, nodeCount={}", nodes != null ? nodes.size() : 0);

            if (nodes == null || nodes.isEmpty()) {
                return "未指定要读取的节点。";
            }

            StringBuilder result = new StringBuilder();
            for (NodeRef nodeRef : nodes) {
                if (!isReadableBySession(nodeRef.docId(), toolContext)) {
                    result.append("## [").append(nodeRef.nodeId()).append("] 无权限访问此节点\n\n");
                    continue;
                }
                String text = documentTreeService.getNodeText(nodeRef.docId(), nodeRef.nodeId());
                result.append("## [").append(nodeRef.nodeId()).append("]\n");
                if (text == null || text.isBlank()) {
                    result.append("(无可用原文)\n\n");
                } else {
                    result.append(text).append("\n\n");
                }
            }

            log.info("[tool][readNodeTexts] success, nodeCount={}, textLen={}", nodes.size(), result.length());
            return result.toString();

        } catch (Exception e) {
            log.error("[tool][readNodeTexts] failed", e);
            return "读取节点原文失败: " + e.getMessage();
        }
    }

    private boolean isReadableBySession(long docId, ToolContext toolContext) {
        if (toolContext == null || toolContext.state() == null) {
            return false;
        }
        Object readableDocIds = toolContext.state().get("user:readableDocIds");
        if (readableDocIds instanceof Iterable<?> iterable) {
            for (Object value : iterable) {
                if (value instanceof Number number && number.longValue() == docId) {
                    return true;
                }
                if (String.valueOf(docId).equals(String.valueOf(value))) {
                    return true;
                }
            }
        }
        return false;
    }
}
