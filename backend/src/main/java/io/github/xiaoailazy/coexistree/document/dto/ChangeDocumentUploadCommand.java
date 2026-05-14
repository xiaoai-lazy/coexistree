package io.github.xiaoailazy.coexistree.document.dto;

/**
 * 将文档上传到指定变更批次（设计 §3.4）；正文仅允许 INSERT，不在上传事务内 UPDATE 既有正文。
 */
public record ChangeDocumentUploadCommand(
        String originalFileName,
        String markdownContent,
        String docContentType,
        Integer securityLevel
) {}
