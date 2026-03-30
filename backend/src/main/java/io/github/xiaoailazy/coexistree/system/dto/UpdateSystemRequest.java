package io.github.xiaoailazy.coexistree.system.dto;

public record UpdateSystemRequest(
        String systemName,
        String description,
        String status
) {
}
