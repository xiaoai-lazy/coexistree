package io.github.xiaoailazy.coexistree.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.llm")
public record LlmProperties(
        @NotBlank
        String apiKey,
        @NotBlank
        String defaultModel,
        @NotBlank
        String baseUrl
) {
}
