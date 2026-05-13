package io.github.xiaoailazy.coexistree.agent.tools;

import com.google.adk.tools.ToolContext;
import io.github.xiaoailazy.coexistree.document.entity.DocumentEntity;
import io.github.xiaoailazy.coexistree.document.repository.DocumentRepository;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * 读取指定文档的内容。
 */
@Slf4j
public class ReadDocumentTool {

    private final DocumentRepository documentRepository;

    public ReadDocumentTool(DocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    public String readDocument(double docId, ToolContext toolContext) {
        try {
            long docIdLong = (long) docId;
            // ===== 第一步：打印工具上下文 =====
            var state = toolContext.state();
            Map<String, Object> ctxSnapshot;
            if (state == null) {
                ctxSnapshot = Map.of("state", "null");
            } else {
                ctxSnapshot = new java.util.LinkedHashMap<>();
                for (var entry : state.entrySet()) {
                    ctxSnapshot.put(entry.getKey(), entry.getValue() != null ? entry.getValue().toString() : "null");
                }
            }
            log.info("[tool][readDocument][ctx] docId={}, stateKeys={}, stateNull={}", docIdLong, ctxSnapshot.keySet(), state == null);

            if (!isReadableBySession(docIdLong, toolContext)) {
                log.warn("[tool][readDocument] denied, docId={}", docIdLong);
                return "无权限访问此文档。";
            }

            log.info("[tool][readDocument] start, docId={}", docIdLong);

            DocumentEntity doc = documentRepository.findById(docIdLong).orElse(null);
            if (doc == null) {
                log.warn("[tool][readDocument] docId={} not found", docIdLong);
                return "文档 ID=" + docIdLong + " 不存在。";
            }

            String content = doc.getFileContent();
            if (content == null || content.isBlank()) {
                log.info("[tool][readDocument] docId={} content is empty, docName={}", docIdLong, doc.getDocName());
                return "文档 " + doc.getDocName() + " 内容为空。";
            }

            log.info("[tool][readDocument] success, docId={}, docName={}, contentLen={}",
                    docIdLong, doc.getDocName(), content.length());
            return content;

        } catch (Exception e) {
            log.error("[tool][readDocument] failed, docId={}", docId, e);
            return "读取文档失败: " + e.getMessage();
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
