package io.github.xiaoailazy.coexistree.system.dto;

import java.time.LocalDateTime;

public record AdminSystemResponse(
        Long id,
        String systemCode,
        String systemName,
        String description,
        String status,
        Long ownerId,
        String ownerUsername,
        Integer memberCount,
        LocalDateTime createdAt
) {
}
