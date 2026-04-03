package io.github.xiaoailazy.coexistree.system.controller;

import io.github.xiaoailazy.coexistree.security.model.SecurityUserDetails;
import io.github.xiaoailazy.coexistree.shared.api.ApiResponse;
import io.github.xiaoailazy.coexistree.system.dto.AddMemberRequest;
import io.github.xiaoailazy.coexistree.system.dto.MemberResponse;
import io.github.xiaoailazy.coexistree.system.dto.UpdateViewLevelRequest;
import io.github.xiaoailazy.coexistree.system.service.MemberService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/systems/{systemId}/members")
public class MemberController {

    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    @GetMapping
    public ApiResponse<List<MemberResponse>> listMembers(
            @PathVariable Long systemId,
            @AuthenticationPrincipal SecurityUserDetails userDetails) {
        return ApiResponse.success(memberService.listMembers(systemId, userDetails));
    }

    @PostMapping
    public ApiResponse<MemberResponse> addMember(
            @PathVariable Long systemId,
            @Valid @RequestBody AddMemberRequest request,
            @AuthenticationPrincipal SecurityUserDetails userDetails) {
        return ApiResponse.success(memberService.addMember(systemId, request, userDetails));
    }

    @PutMapping("/{userId}/view-level")
    public ApiResponse<MemberResponse> updateViewLevel(
            @PathVariable Long systemId,
            @PathVariable Long userId,
            @Valid @RequestBody UpdateViewLevelRequest request,
            @AuthenticationPrincipal SecurityUserDetails userDetails) {
        return ApiResponse.success(memberService.updateViewLevel(systemId, userId, request, userDetails));
    }

    @DeleteMapping("/{userId}")
    public ApiResponse<Void> removeMember(
            @PathVariable Long systemId,
            @PathVariable Long userId,
            @AuthenticationPrincipal SecurityUserDetails userDetails) {
        memberService.removeMember(systemId, userId, userDetails);
        return ApiResponse.success(null);
    }
}
