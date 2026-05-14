package io.github.xiaoailazy.coexistree.agent.tools;

import com.google.adk.tools.ToolContext;
import io.github.xiaoailazy.coexistree.document.repository.DocumentRepository;
import io.github.xiaoailazy.coexistree.document.service.DocumentAccessService;
import io.github.xiaoailazy.coexistree.document.service.DocumentTreeService;
import io.github.xiaoailazy.coexistree.security.model.SecurityUserDetails;
import io.github.xiaoailazy.coexistree.user.entity.UserRole;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 批量读取知识树节点在文档树中的原文；与树上 {@code evidenceSources}/{@code sources} 指针一致（docId + nodeId）。
 */
@Slf4j
public class ReadNodeTextTool {

    private final DocumentTreeService documentTreeService;
    private final DocumentRepository documentRepository;
    private final DocumentAccessService documentAccessService;

    public ReadNodeTextTool(DocumentTreeService documentTreeService) {
        this(documentTreeService, null, null);
    }

    public ReadNodeTextTool(
            DocumentTreeService documentTreeService,
            DocumentRepository documentRepository,
            DocumentAccessService documentAccessService) {
        this.documentTreeService = documentTreeService;
        this.documentRepository = documentRepository;
        this.documentAccessService = documentAccessService;
    }

    /** 与 {@code evidenceSources} / LLM 工具 JSON 一致的节点引用。 */
    public record NodeRef(long docId, String nodeId) {}

    public String readNodeTexts(List<NodeRef> nodes, ToolContext toolContext) {
        try {
            log.info("[tool][readNodeTexts] start, nodeCount={}", nodes != null ? nodes.size() : 0);

            if (nodes == null || nodes.isEmpty()) {
                return "未指定要读取的节点。";
            }

            StringBuilder result = new StringBuilder();
            for (NodeRef nodeRef : nodes) {
                if (!canReadDocument(nodeRef.docId(), toolContext)) {
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

    private boolean canReadDocument(long docId, ToolContext toolContext) {
        SecurityUserDetails user = userFromToolState(toolContext);
        if (documentRepository != null && documentAccessService != null && user != null) {
            return documentRepository
                    .findById(docId)
                    .map(d -> documentAccessService.canReadDocument(d, user))
                    .orElse(false);
        }
        return isReadableByReadableDocIdsList(docId, toolContext);
    }

    private static SecurityUserDetails userFromToolState(ToolContext toolContext) {
        if (toolContext == null || toolContext.state() == null) {
            return null;
        }
        Object uid = toolContext.state().get("user:userId");
        if (!(uid instanceof Number n)) {
            return null;
        }
        UserRole role = UserRole.USER;
        Object roleStr = toolContext.state().get("user:userRole");
        if (roleStr != null) {
            try {
                role = UserRole.valueOf(String.valueOf(roleStr));
            } catch (IllegalArgumentException ignored) {
                // keep USER
            }
        }
        return SecurityUserDetails.forAccessCheck(n.longValue(), role);
    }

    private static boolean isReadableByReadableDocIdsList(long docId, ToolContext toolContext) {
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
