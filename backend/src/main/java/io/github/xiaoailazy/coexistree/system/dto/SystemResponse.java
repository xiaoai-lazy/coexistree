package io.github.xiaoailazy.coexistree.system.dto;

public record SystemResponse(
        Long id,
        String systemCode,
        String systemName,
        String description,
        String status
) {
}

