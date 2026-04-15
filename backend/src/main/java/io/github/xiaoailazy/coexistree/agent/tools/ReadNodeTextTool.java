package io.github.xiaoailazy.coexistree.agent.tools;

import io.github.xiaoailazy.coexistree.document.service.DocumentTreeService;
import lombok.extern.slf4j.Slf4j;

/**
 * 读取知识树节点的原文。
 */
@Slf4j
public class ReadNodeTextTool {

    private final DocumentTreeService documentTreeService;

    public ReadNodeTextTool(DocumentTreeService documentTreeService) {
        this.documentTreeService = documentTreeService;
    }

    public String execute(Long docId, String nodeId) {
        try {
            String text = documentTreeService.getNodeText(docId, nodeId);
            if (text == null || text.isBlank()) {
                return "节点 " + nodeId + " 无可用原文。";
            }
            return text;
        } catch (Exception e) {
            log.error("read_node_text 执行失败, docId={}, nodeId={}", docId, nodeId, e);
            return "读取节点原文失败: " + e.getMessage();
        }
    }
}
