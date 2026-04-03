package io.github.xiaoailazy.coexistree.system.dto;

import io.github.xiaoailazy.coexistree.system.entity.RelationType;

import java.time.LocalDateTime;

public class MemberResponse {
    private Long id;
    private Long userId;
    private String username;
    private String displayName;
    private RelationType relationType;
    private Integer viewLevel;
    private LocalDateTime assignedAt;

    public MemberResponse(Long id, Long userId, String username, String displayName,
                         RelationType relationType, Integer viewLevel, LocalDateTime assignedAt) {
        this.id = id;
        this.userId = userId;
        this.username = username;
        this.displayName = displayName;
        this.relationType = relationType;
        this.viewLevel = viewLevel;
        this.assignedAt = assignedAt;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getUsername() { return username; }
    public String getDisplayName() { return displayName; }
    public RelationType getRelationType() { return relationType; }
    public Integer getViewLevel() { return viewLevel; }
    public LocalDateTime getAssignedAt() { return assignedAt; }
}
