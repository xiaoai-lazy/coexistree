package io.github.xiaoailazy.coexistree.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CurrentUserResponse {
    private Long id;
    private String username;
    private String displayName;
    private String role;
}
