package io.github.xiaoailazy.coexistree.document.controller;

import io.github.xiaoailazy.coexistree.document.dto.DocumentResponse;
import io.github.xiaoailazy.coexistree.document.service.DocumentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DocumentController.class)
class DocumentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DocumentService documentService;

    @Test
    void testListAllDocuments() throws Exception {
        // Given
        DocumentResponse doc1 = createDocumentResponse(1L, "doc1.md", "SUCCESS");
        DocumentResponse doc2 = createDocumentResponse(2L, "doc2.md", "PROCESSING");

        when(documentService.list(null)).thenReturn(List.of(doc1, doc2));

        // When & Then
        mockMvc.perform(get("/api/v1/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[1].id").value(2));
    }

    @Test
    void testListDocumentsBySystemId() throws Exception {
        // Given
        Long systemId = 1L;
        DocumentResponse doc = createDocumentResponse(1L, "doc.md", "SUCCESS");

        when(documentService.list(systemId)).thenReturn(List.of(doc));

        // When & Then
        mockMvc.perform(get("/api/v1/documents")
                        .param("systemId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].systemId").value(1));
    }

    @Test
    void testUploadDocument() throws Exception {
        // Given
        Long systemId = 1L;
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.md",
                MediaType.TEXT_PLAIN_VALUE,
                "# 测试文档".getBytes()
        );

        DocumentResponse response = createDocumentResponse(1L, "test.md", "PROCESSING");
        when(documentService.upload(eq(systemId), any())).thenReturn(response);

        // When & Then
        mockMvc.perform(multipart("/api/v1/documents/upload")
                        .file(file)
                        .param("systemId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.originalFileName").value("test.md"));
    }

    @Test
    void testGetDocumentById() throws Exception {
        // Given
        Long documentId = 1L;
        DocumentResponse response = createDocumentResponse(documentId, "guide.md", "SUCCESS");

        when(documentService.getById(documentId)).thenReturn(response);

        // When & Then
        mockMvc.perform(get("/api/v1/documents/{id}", documentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.originalFileName").value("guide.md"))
                .andExpect(jsonPath("$.data.parseStatus").value("SUCCESS"));
    }

    @Test
    void testDeleteDocument() throws Exception {
        // Given
        Long documentId = 1L;
        doNothing().when(documentService).delete(documentId);

        // When & Then
        mockMvc.perform(delete("/api/v1/documents/{id}", documentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void testListEmptyDocuments() throws Exception {
        // Given
        when(documentService.list(null)).thenReturn(List.of());

        // When & Then
        mockMvc.perform(get("/api/v1/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    void testUploadLargeFile() throws Exception {
        // Given
        Long systemId = 1L;
        byte[] largeContent = new byte[1024 * 1024]; // 1MB
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "large.md",
                MediaType.TEXT_PLAIN_VALUE,
                largeContent
        );

        DocumentResponse response = createDocumentResponse(1L, "large.md", "PROCESSING");
        when(documentService.upload(eq(systemId), any())).thenReturn(response);

        // When & Then
        mockMvc.perform(multipart("/api/v1/documents/upload")
                        .file(file)
                        .param("systemId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.originalFileName").value("large.md"));
    }

    private DocumentResponse createDocumentResponse(Long id, String filename, String status) {
        return new DocumentResponse(
                id,
                1L,
                filename.replace(".md", ""),
                filename,
                status,
                null,
                LocalDateTime.now()
        );
    }
}
