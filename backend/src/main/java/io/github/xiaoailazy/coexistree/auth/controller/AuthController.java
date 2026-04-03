package io.github.xiaoailazy.coexistree.auth.controller;

import io.github.xiaoailazy.coexistree.auth.dto.*;
import io.github.xiaoailazy.coexistree.auth.service.AuthService;
import io.github.xiaoailazy.coexistree.security.model.SecurityUserDetails;
import io.github.xiaoailazy.coexistree.shared.api.ApiResponse;
import io.github.xiaoailazy.coexistree.user.entity.UserEntity;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    @GetMapping("/me")
    public ApiResponse<CurrentUserResponse> getCurrentUser(@AuthenticationPrincipal SecurityUserDetails userDetails) {
        UserEntity user = authService.getCurrentUser(userDetails.getId());
        return ApiResponse.success(new CurrentUserResponse(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getRole().name()
        ));
    }

    @PutMapping("/password")
    public ApiResponse<Void> changePassword(
            @AuthenticationPrincipal SecurityUserDetails userDetails,
            @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(userDetails.getId(), request);
        return ApiResponse.success(null);
    }
}
