package io.github.xiaoailazy.coexistree.system.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateSystemRequest(
        @NotBlank String systemCode,
        @NotBlank String systemName,
        String description
) {
}

