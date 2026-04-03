package io.github.xiaoailazy.coexistree.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {
    private String token;
    private String tokenType = "Bearer";
    private Long userId;
    private String username;
    private String displayName;
    private String role;

    public LoginResponse(String token, Long userId, String username, String displayName, String role) {
        this.token = token;
        this.userId = userId;
        this.username = username;
        this.displayName = displayName;
        this.role = role;
        this.tokenType = "Bearer";
    }
}
