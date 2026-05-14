package io.github.xiaoailazy.coexistree.security.model;

import io.github.xiaoailazy.coexistree.user.entity.UserEntity;
import io.github.xiaoailazy.coexistree.user.entity.UserRole;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

public class SecurityUserDetails implements UserDetails {

    private final Long id;
    private final String username;
    private final String password;
    private final UserRole role;
    private final Collection<? extends GrantedAuthority> authorities;

    public SecurityUserDetails(UserEntity user) {
        this.id = user.getId();
        this.username = user.getUsername();
        this.password = user.getPasswordHash();
        this.role = user.getRole();
        this.authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
    }

    /**
     * 用于 Agent 会话等场景：仅依赖 userId + role 做文档级权限判断（与 {@link UserEntity} 完整字段解耦）。
     */
    public static SecurityUserDetails forAccessCheck(Long userId, UserRole role) {
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setUsername("_session_");
        user.setPasswordHash("-");
        user.setDisplayName("_");
        user.setRole(role != null ? role : UserRole.USER);
        user.setEnabled(true);
        return new SecurityUserDetails(user);
    }

    public Long getId() { return id; }
    public UserRole getRole() { return role; }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() { return authorities; }

    @Override
    public String getPassword() { return password; }

    @Override
    public String getUsername() { return username; }

    @Override
    public boolean isAccountNonExpired() { return true; }

    @Override
    public boolean isAccountNonLocked() { return true; }

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    @Override
    public boolean isEnabled() { return true; }
}
