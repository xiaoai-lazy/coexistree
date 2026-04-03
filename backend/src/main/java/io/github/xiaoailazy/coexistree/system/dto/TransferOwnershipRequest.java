package io.github.xiaoailazy.coexistree.system.dto;

import jakarta.validation.constraints.NotNull;

public record TransferOwnershipRequest(
        @NotNull(message = "新所有者ID不能为空")
        Long newOwnerId
) {
}
