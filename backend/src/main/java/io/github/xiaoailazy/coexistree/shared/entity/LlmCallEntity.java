package io.github.xiaoailazy.coexistree.shared.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "llm_calls")
public class LlmCallEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "scenario", nullable = false, length = 128)
    private String scenario;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "document_id")
    private Long documentId;

    @Column(name = "system_id")
    private Long systemId;

    @Column(name = "model", nullable = false, length = 64)
    private String model;

    @Column(name = "temperature")
    private Double temperature;

    @Column(name = "input_tokens")
    private Long inputTokens;

    @Column(name = "output_tokens")
    private Long outputTokens;

    @Column(name = "reasoning_tokens")
    private Long reasoningTokens;

    @Column(name = "total_tokens")
    private Long totalTokens;

    @Column(name = "elapsed_ms", nullable = false)
    private Long elapsedMs;

    @Column(name = "success", nullable = false)
    private Boolean success = true;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getScenario() { return scenario; }
    public void setScenario(String scenario) { this.scenario = scenario; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getDocumentId() { return documentId; }
    public void setDocumentId(Long documentId) { this.documentId = documentId; }
    public Long getSystemId() { return systemId; }
    public void setSystemId(Long systemId) { this.systemId = systemId; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public Double getTemperature() { return temperature; }
    public void setTemperature(Double temperature) { this.temperature = temperature; }
    public Long getInputTokens() { return inputTokens; }
    public void setInputTokens(Long inputTokens) { this.inputTokens = inputTokens; }
    public Long getOutputTokens() { return outputTokens; }
    public void setOutputTokens(Long outputTokens) { this.outputTokens = outputTokens; }
    public Long getReasoningTokens() { return reasoningTokens; }
    public void setReasoningTokens(Long reasoningTokens) { this.reasoningTokens = reasoningTokens; }
    public Long getTotalTokens() { return totalTokens; }
    public void setTotalTokens(Long totalTokens) { this.totalTokens = totalTokens; }
    public Long getElapsedMs() { return elapsedMs; }
    public void setElapsedMs(Long elapsedMs) { this.elapsedMs = elapsedMs; }
    public Boolean isSuccess() { return success; }
    public void setSuccess(Boolean success) { this.success = success; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
