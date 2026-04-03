package io.github.xiaoailazy.coexistree.system.controller;

import io.github.xiaoailazy.coexistree.security.model.SecurityUserDetails;
import io.github.xiaoailazy.coexistree.shared.api.ApiResponse;
import io.github.xiaoailazy.coexistree.system.dto.AdminSystemResponse;
import io.github.xiaoailazy.coexistree.system.dto.CreateSystemRequest;
import io.github.xiaoailazy.coexistree.system.dto.SystemResponse;
import io.github.xiaoailazy.coexistree.system.dto.TransferOwnershipRequest;
import io.github.xiaoailazy.coexistree.system.dto.UpdateSystemRequest;
import io.github.xiaoailazy.coexistree.system.service.SystemService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/systems")
public class SystemController {

    private final SystemService systemService;

    public SystemController(SystemService systemService) {
        this.systemService = systemService;
    }

    @GetMapping
    public ApiResponse<List<SystemResponse>> list(
            @AuthenticationPrincipal SecurityUserDetails userDetails) {
        return ApiResponse.success(systemService.list(userDetails));
    }

    @PostMapping
    public ApiResponse<SystemResponse> create(
            @Valid @RequestBody CreateSystemRequest request,
            @AuthenticationPrincipal SecurityUserDetails userDetails) {
        return ApiResponse.success(systemService.create(request, userDetails));
    }

    @GetMapping("/{id}")
    public ApiResponse<SystemResponse> get(@PathVariable Long id) {
        return ApiResponse.success(systemService.get(id));
    }

    @PutMapping("/{id}")
    public ApiResponse<SystemResponse> update(@PathVariable Long id, @RequestBody UpdateSystemRequest request) {
        return ApiResponse.success(systemService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        systemService.delete(id);
        return ApiResponse.success(null);
    }

    // Admin endpoints

    @GetMapping("/admin/all")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<List<AdminSystemResponse>> listAllForAdmin() {
        return ApiResponse.success(systemService.listAllForAdmin());
    }

    @PutMapping("/{id}/transfer")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<Void> transferOwnership(
            @PathVariable Long id,
            @Valid @RequestBody TransferOwnershipRequest request) {
        systemService.transferOwnership(id, request.newOwnerId());
        return ApiResponse.success(null);
    }
}
