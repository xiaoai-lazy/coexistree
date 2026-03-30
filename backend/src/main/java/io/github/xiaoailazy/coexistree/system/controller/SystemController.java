package io.github.xiaoailazy.coexistree.system.controller;

import io.github.xiaoailazy.coexistree.shared.api.ApiResponse;
import io.github.xiaoailazy.coexistree.system.dto.CreateSystemRequest;
import io.github.xiaoailazy.coexistree.system.dto.SystemResponse;
import io.github.xiaoailazy.coexistree.system.dto.UpdateSystemRequest;
import io.github.xiaoailazy.coexistree.system.service.SystemService;
import jakarta.validation.Valid;
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
    public ApiResponse<List<SystemResponse>> list() {
        return ApiResponse.success(systemService.list());
    }

    @PostMapping
    public ApiResponse<SystemResponse> create(@Valid @RequestBody CreateSystemRequest request) {
        return ApiResponse.success(systemService.create(request));
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
}
