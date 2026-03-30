package io.github.xiaoailazy.coexistree.document.service;

import io.github.xiaoailazy.coexistree.document.dto.DocumentResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface DocumentService {

    DocumentResponse upload(Long systemId, MultipartFile file);

    DocumentResponse getById(Long documentId);

    List<DocumentResponse> list(Long systemId);

    void delete(Long documentId);
}
