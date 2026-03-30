package io.github.xiaoailazy.coexistree.document.controller;

import io.github.xiaoailazy.coexistree.shared.api.ApiResponse;
import io.github.xiaoailazy.coexistree.document.dto.DocumentResponse;
import io.github.xiaoailazy.coexistree.document.service.DocumentService;
import lombok.extern.slf4j.Slf4j;
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
    public ApiResponse<List<DocumentResponse>> list(
            @RequestParam(required = false) Long systemId
    ) {
        log.debug("查询文档列表, systemId={}", systemId);
        return ApiResponse.success(documentService.list(systemId));
    }

    @PostMapping("/upload")
    public ApiResponse<DocumentResponse> upload(
            @RequestParam Long systemId,
            @RequestParam MultipartFile file
    ) {
        log.debug("上传文档, systemId={}, fileName={}", systemId, file.getOriginalFilename());
        return ApiResponse.success(documentService.upload(systemId, file));
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
}
