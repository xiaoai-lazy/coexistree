package io.github.xiaoailazy.coexistree.document.event;

/**
 * 文档上传完成事件
 * 触发异步文档树构建和系统树合并
 */
public record DocumentUploadedEvent(Long documentId) {
}
