package io.github.xiaoailazy.coexistree.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "llm.openai")
public class LlmProperties {

    @NotBlank
    private String apiKey;

    @NotBlank
    private String baseUrl = "https://api.openai.com/v1";

    @NotBlank
    private String model = "gpt-4o-mini";

    private double temperature = 0.3;

    @Positive
    private int maxTokens = 4096;

    @Positive
    private int timeout = 60000;

    @Positive
    private int retryAttempts = 3;

    private boolean enabled = true;

    // Getters and Setters
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public double getTemperature() { return temperature; }
    public void setTemperature(double temperature) { this.temperature = temperature; }

    public int getMaxTokens() { return maxTokens; }
    public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }

    public int getTimeout() { return timeout; }
    public void setTimeout(int timeout) { this.timeout = timeout; }

    public int getRetryAttempts() { return retryAttempts; }
    public void setRetryAttempts(int retryAttempts) { this.retryAttempts = retryAttempts; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
