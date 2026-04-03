package io.github.xiaoailazy.coexistree.user.dto;

import jakarta.validation.constraints.*;

public class UpdateUserRequest {

    @NotBlank(message = "显示名称不能为空")
    @Size(max = 128, message = "显示名称长度不能超过128")
    private String displayName;

    @NotBlank(message = "角色不能为空")
    private String role;

    @NotNull(message = "状态不能为空")
    private Boolean enabled;

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
}
