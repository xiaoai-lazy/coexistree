package io.github.xiaoailazy.coexistree.user.dto;

import jakarta.validation.constraints.*;

public class CreateUserRequest {
    @NotBlank(message = "用户名不能为空")
    @Size(max = 64, message = "用户名长度不能超过64")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,}$",
            message = "密码必须至少8位，包含大小写字母和数字")
    private String password;

    @NotBlank(message = "显示名称不能为空")
    @Size(max = 128, message = "显示名称长度不能超过128")
    private String displayName;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
}
