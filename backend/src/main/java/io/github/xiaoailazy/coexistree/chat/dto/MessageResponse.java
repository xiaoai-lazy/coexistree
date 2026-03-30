package io.github.xiaoailazy.coexistree.chat.dto;

import io.github.xiaoailazy.coexistree.chat.dto.SseEvent;

import java.time.LocalDateTime;
import java.util.List;

public record MessageResponse(
        Long id,
        String role,
        String content,
        String thinking,
        List<SseEvent.CitationDto> citations,
        LocalDateTime createdAt
) {}
