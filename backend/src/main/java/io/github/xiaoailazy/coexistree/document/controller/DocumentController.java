package io.github.xiaoailazy.coexistree.document.controller;

import io.github.xiaoailazy.coexistree.document.dto.DocumentContentResponse;
import io.github.xiaoailazy.coexistree.document.dto.DocumentResponse;
import io.github.xiaoailazy.coexistree.document.service.DocumentService;
import io.github.xiaoailazy.coexistree.security.model.SecurityUserDetails;
import io.github.xiaoailazy.coexistree.shared.api.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @GetMapping
    public ApiResponse<List<DocumentResponse>> listBySystem(
            @RequestParam Long systemId,
            @AuthenticationPrincipal SecurityUserDetails userDetails) {
        log.debug("查询文档列表, systemId={}", systemId);
        return ApiResponse.success(documentService.listBySystem(systemId, userDetails));
    }

    @PostMapping("/upload")
    public ApiResponse<DocumentResponse> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("systemId") Long systemId,
            @RequestParam(value = "securityLevel", defaultValue = "1") Integer securityLevel,
            @AuthenticationPrincipal SecurityUserDetails userDetails) {
        log.debug("上传文档, systemId={}, fileName={}", systemId, file.getOriginalFilename());
        return ApiResponse.success(documentService.upload(file, systemId, securityLevel, userDetails));
    }

    @PutMapping("/{id}/security-level")
    public ApiResponse<Void> updateSecurityLevel(
            @PathVariable Long id,
            @RequestParam Integer securityLevel,
            @AuthenticationPrincipal SecurityUserDetails userDetails) {
        documentService.updateSecurityLevel(id, securityLevel, userDetails);
        return ApiResponse.success(null);
    }

    @GetMapping("/{id}")
    public ApiResponse<DocumentResponse> get(@PathVariable Long id) {
        log.debug("查询文档, documentId={}", id);
        return ApiResponse.success(documentService.getById(id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        log.debug("删除文档, documentId={}", id);
        documentService.delete(id);
        return ApiResponse.success(null);
    }

    @GetMapping("/{id}/content")
    public ApiResponse<DocumentContentResponse> getContent(
            @PathVariable Long id,
            @AuthenticationPrincipal SecurityUserDetails userDetails) {
        log.debug("获取文档内容, documentId={}", id);
        return ApiResponse.success(documentService.getContent(id, userDetails));
    }
}
