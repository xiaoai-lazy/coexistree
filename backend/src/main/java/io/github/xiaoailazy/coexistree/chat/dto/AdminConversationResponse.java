package io.github.xiaoailazy.coexistree.chat.dto;

import java.time.LocalDateTime;

public record AdminConversationResponse(
        String conversationId,
        Long systemId,
        String systemName,
        String title,
        String username,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        long eventCount
) {}
