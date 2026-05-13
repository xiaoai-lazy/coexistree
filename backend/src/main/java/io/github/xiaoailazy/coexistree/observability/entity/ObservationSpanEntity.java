package io.github.xiaoailazy.coexistree.observability.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "observation_spans")
public class ObservationSpanEntity {

    @Id
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "trace_id", nullable = false, length = 64)
    private String traceId;

    @Column(name = "span_id", nullable = false, length = 64)
    private String spanId;

    @Column(name = "parent_span_id", length = 64)
    private String parentSpanId;

    @Column(name = "run_id", nullable = false, length = 64)
    private String runId;

    @Column(name = "conversation_id", nullable = false, length = 64)
    private String conversationId;

    @Column(name = "span_type", nullable = false, length = 32)
    private String spanType;

    @Column(name = "span_name", nullable = false, length = 128)
    private String spanName;

    @Column(name = "agent_name", length = 128)
    private String agentName;

    @Column(name = "tool_name", length = 128)
    private String toolName;

    @Column(name = "model_name", length = 128)
    private String modelName;

    @Column(name = "model_version", length = 128)
    private String modelVersion;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "input_payload", columnDefinition = "TEXT")
    private String inputPayload;

    @Column(name = "output_payload", columnDefinition = "TEXT")
    private String outputPayload;

    @Column(name = "attributes", columnDefinition = "TEXT")
    private String attributes;

    @Column(name = "error_payload", columnDefinition = "TEXT")
    private String errorPayload;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "depth")
    private Integer depth;

    @Column(name = "sequence_no")
    private Long sequenceNo;

    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
    public String getSpanId() { return spanId; }
    public void setSpanId(String spanId) { this.spanId = spanId; }
    public String getParentSpanId() { return parentSpanId; }
    public void setParentSpanId(String parentSpanId) { this.parentSpanId = parentSpanId; }
    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }
    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }
    public String getSpanType() { return spanType; }
    public void setSpanType(String spanType) { this.spanType = spanType; }
    public String getSpanName() { return spanName; }
    public void setSpanName(String spanName) { this.spanName = spanName; }
    public String getAgentName() { return agentName; }
    public void setAgentName(String agentName) { this.agentName = agentName; }
    public String getToolName() { return toolName; }
    public void setToolName(String toolName) { this.toolName = toolName; }
    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }
    public String getModelVersion() { return modelVersion; }
    public void setModelVersion(String modelVersion) { this.modelVersion = modelVersion; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getInputPayload() { return inputPayload; }
    public void setInputPayload(String inputPayload) { this.inputPayload = inputPayload; }
    public String getOutputPayload() { return outputPayload; }
    public void setOutputPayload(String outputPayload) { this.outputPayload = outputPayload; }
    public String getAttributes() { return attributes; }
    public void setAttributes(String attributes) { this.attributes = attributes; }
    public String getErrorPayload() { return errorPayload; }
    public void setErrorPayload(String errorPayload) { this.errorPayload = errorPayload; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(LocalDateTime finishedAt) { this.finishedAt = finishedAt; }
    public Long getDurationMs() { return durationMs; }
    public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }
    public Integer getDepth() { return depth; }
    public void setDepth(Integer depth) { this.depth = depth; }
    public Long getSequenceNo() { return sequenceNo; }
    public void setSequenceNo(Long sequenceNo) { this.sequenceNo = sequenceNo; }
    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
