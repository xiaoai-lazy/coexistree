package io.github.xiaoailazy.coexistree.chat.dto;

import java.time.LocalDateTime;

public record ConversationResponse(
        String conversationId,
        Long systemId,
        String title,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
