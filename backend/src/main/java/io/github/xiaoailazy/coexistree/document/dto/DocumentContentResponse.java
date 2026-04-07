package io.github.xiaoailazy.coexistree.document.dto;

import java.util.List;

public record DocumentContentResponse(
        Long docId,
        String docName,
        String contentType,     // "text/markdown", "application/pdf", etc.
        String content,         // text content for markdown
        String downloadUrl,     // download link for binary files
        List<NodeAnchor> anchors
) {}
