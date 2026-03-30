package io.github.xiaoailazy.coexistree.document.dto;

import java.time.LocalDateTime;

public record DocumentResponse(
        Long id,
        Long systemId,
        String docName,
        String originalFileName,
        String parseStatus,
        String parseError,
        LocalDateTime createdAt
) {
}
