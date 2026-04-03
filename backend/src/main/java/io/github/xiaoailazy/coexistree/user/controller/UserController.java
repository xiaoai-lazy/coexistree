package io.github.xiaoailazy.coexistree.user.controller;

import io.github.xiaoailazy.coexistree.security.model.SecurityUserDetails;
import io.github.xiaoailazy.coexistree.shared.api.ApiResponse;
import io.github.xiaoailazy.coexistree.user.dto.CreateUserRequest;
import io.github.xiaoailazy.coexistree.user.dto.UpdateUserRequest;
import io.github.xiaoailazy.coexistree.user.dto.UserResponse;
import io.github.xiaoailazy.coexistree.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<List<UserResponse>> list() {
        return ApiResponse.success(userService.listAll());
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<UserResponse> create(
            @Valid @RequestBody CreateUserRequest request,
            @AuthenticationPrincipal SecurityUserDetails currentUser) {
        return ApiResponse.success(userService.createUser(request, currentUser.getId()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        userService.deleteUser(id);
        return ApiResponse.success(null);
    }

    @PutMapping("/{id}/password")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<Void> resetPassword(
            @PathVariable Long id,
            @RequestBody String newPassword) {
        userService.resetPassword(id, newPassword);
        return ApiResponse.success(null);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<UserResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserRequest request) {
        return ApiResponse.success(userService.updateUser(id, request));
    }
}
