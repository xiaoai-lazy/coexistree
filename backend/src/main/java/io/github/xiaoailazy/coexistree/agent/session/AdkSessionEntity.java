package io.github.xiaoailazy.coexistree.agent.session;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "adk_sessions")
public class AdkSessionEntity {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "app_name", length = 128, nullable = false)
    private String appName;

    @Column(name = "user_id", length = 128, nullable = false)
    private String userId;

    @Column(name = "state_json", columnDefinition = "TEXT")
    private String stateJson;

    @Column(name = "create_time", nullable = false)
    private LocalDateTime createTime;

    @Column(name = "update_time", nullable = false)
    private LocalDateTime updateTime;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getAppName() { return appName; }
    public void setAppName(String appName) { this.appName = appName; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getStateJson() { return stateJson; }
    public void setStateJson(String stateJson) { this.stateJson = stateJson; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
