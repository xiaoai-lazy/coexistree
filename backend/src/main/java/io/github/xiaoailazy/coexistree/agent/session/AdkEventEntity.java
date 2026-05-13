package io.github.xiaoailazy.coexistree.agent.session;

import jakarta.persistence.*;

@Entity
@Table(name = "adk_events")
public class AdkEventEntity {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "session_id", length = 64, nullable = false)
    private String sessionId;

    @Column(name = "app_name", length = 128, nullable = false)
    private String appName;

    @Column(name = "user_id", length = 128, nullable = false)
    private String userId;

    @Column(name = "author", length = 128)
    private String author;

    @Column(name = "event_json", columnDefinition = "TEXT", nullable = false)
    private String eventJson;

    @Column(name = "timestamp", nullable = false)
    private Long timestamp;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getAppName() { return appName; }
    public void setAppName(String appName) { this.appName = appName; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }
    public String getEventJson() { return eventJson; }
    public void setEventJson(String eventJson) { this.eventJson = eventJson; }
    public Long getTimestamp() { return timestamp; }
    public void setTimestamp(Long timestamp) { this.timestamp = timestamp; }
}
