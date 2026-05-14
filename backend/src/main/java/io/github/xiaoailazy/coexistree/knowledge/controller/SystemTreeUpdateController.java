package io.github.xiaoailazy.coexistree.knowledge.controller;

import io.github.xiaoailazy.coexistree.change.entity.SystemChangeRecordEntity;
import io.github.xiaoailazy.coexistree.change.repository.SystemChangeRecordRepository;
import io.github.xiaoailazy.coexistree.document.service.DocumentAccessService;
import io.github.xiaoailazy.coexistree.knowledge.service.SystemTreeUpdateService;
import io.github.xiaoailazy.coexistree.security.model.SecurityUserDetails;
import io.github.xiaoailazy.coexistree.shared.api.ApiResponse;
import io.github.xiaoailazy.coexistree.shared.enums.ErrorCode;
import io.github.xiaoailazy.coexistree.shared.exception.BusinessException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/systems/{systemId}/changes")
@Tag(name = "System knowledge tree", description = "按变更批次应用系统树（applyChange）")
public class SystemTreeUpdateController {

    private final SystemTreeUpdateService systemTreeUpdateService;
    private final SystemChangeRecordRepository systemChangeRecordRepository;
    private final DocumentAccessService documentAccessService;

    public SystemTreeUpdateController(
            SystemTreeUpdateService systemTreeUpdateService,
            SystemChangeRecordRepository systemChangeRecordRepository,
            DocumentAccessService documentAccessService) {
        this.systemTreeUpdateService = systemTreeUpdateService;
        this.systemChangeRecordRepository = systemChangeRecordRepository;
        this.documentAccessService = documentAccessService;
    }

    @PostMapping("/{changeRecordId}/apply")
    @Operation(summary = "应用变更批次", description = "将已准备好的变更记录应用到当前系统的 ACTIVE 知识树（设计 §6 applyChange）。")
    public ApiResponse<Void> applyChange(
            @PathVariable Long systemId,
            @PathVariable long changeRecordId,
            @AuthenticationPrincipal SecurityUserDetails userDetails) {
        documentAccessService.checkSystemAccess(systemId, userDetails);
        SystemChangeRecordEntity record =
                systemChangeRecordRepository
                        .findById(changeRecordId)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                ErrorCode.APPLY_PRECONDITION_FAILED,
                                                "Change record not found: " + changeRecordId));
        if (!systemId.equals(record.getSystemId())) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED, "变更记录不属于当前系统");
        }
        systemTreeUpdateService.applyChange(changeRecordId);
        return ApiResponse.success(null);
    }
}
