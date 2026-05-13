package io.github.xiaoailazy.coexistree.observability.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "conversation_runs")
public class ConversationRunEntity {

    @Id
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "run_id", nullable = false, length = 64, unique = true)
    private String runId;

    @Column(name = "trace_id", nullable = false, length = 64)
    private String traceId;

    @Column(name = "conversation_id", nullable = false, length = 64)
    private String conversationId;

    @Column(name = "trigger_message_id")
    private Long triggerMessageId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "system_id")
    private Long systemId;

    @Column(name = "root_agent_name", length = 128)
    private String rootAgentName;

    @Column(name = "request_text", columnDefinition = "TEXT")
    private String requestText;

    @Column(name = "final_answer", columnDefinition = "TEXT")
    private String finalAnswer;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "error_code", length = 64)
    private String errorCode;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }
    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }
    public Long getTriggerMessageId() { return triggerMessageId; }
    public void setTriggerMessageId(Long triggerMessageId) { this.triggerMessageId = triggerMessageId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getSystemId() { return systemId; }
    public void setSystemId(Long systemId) { this.systemId = systemId; }
    public String getRootAgentName() { return rootAgentName; }
    public void setRootAgentName(String rootAgentName) { this.rootAgentName = rootAgentName; }
    public String getRequestText() { return requestText; }
    public void setRequestText(String requestText) { this.requestText = requestText; }
    public String getFinalAnswer() { return finalAnswer; }
    public void setFinalAnswer(String finalAnswer) { this.finalAnswer = finalAnswer; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(LocalDateTime finishedAt) { this.finishedAt = finishedAt; }
    public Long getDurationMs() { return durationMs; }
    public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }
    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
