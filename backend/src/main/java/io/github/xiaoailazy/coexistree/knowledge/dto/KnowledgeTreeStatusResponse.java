package io.github.xiaoailazy.coexistree.knowledge.dto;

import java.time.LocalDateTime;

public record KnowledgeTreeStatusResponse(
        Integer treeVersion,
        Integer nodeCount,
        String treeStatus,
        LocalDateTime lastUpdatedAt
) {
}
