package io.github.xiaoailazy.coexistree.chat.dto;

/**
 * 对话请求（支持文档上传）
 */
public record ChatRequest(
        String question,
        Long documentId
) {
    /**
     * 创建纯问答请求
     */
    public static ChatRequest question(String question) {
        return new ChatRequest(question, null);
    }

    /**
     * 创建带文档的请求
     */
    public static ChatRequest withDocument(String question, Long documentId) {
        return new ChatRequest(question, documentId);
    }

    /**
     * 是否包含文档
     */
    public boolean hasDocument() {
        return documentId != null;
    }
}
