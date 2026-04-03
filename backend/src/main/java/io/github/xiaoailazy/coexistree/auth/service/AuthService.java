package io.github.xiaoailazy.coexistree.auth.service;

import io.github.xiaoailazy.coexistree.auth.dto.ChangePasswordRequest;
import io.github.xiaoailazy.coexistree.auth.dto.LoginRequest;
import io.github.xiaoailazy.coexistree.auth.dto.LoginResponse;
import io.github.xiaoailazy.coexistree.user.entity.UserEntity;

public interface AuthService {
    LoginResponse login(LoginRequest request);
    void changePassword(Long userId, ChangePasswordRequest request);
    UserEntity getCurrentUser(Long userId);
}
