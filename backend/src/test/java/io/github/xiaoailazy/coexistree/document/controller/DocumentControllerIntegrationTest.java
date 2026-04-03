package io.github.xiaoailazy.coexistree.document.controller;

import io.github.xiaoailazy.coexistree.document.dto.DocumentResponse;
import io.github.xiaoailazy.coexistree.document.service.DocumentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DocumentControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DocumentService documentService;

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void shouldUploadMarkdownDocument() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.md",
                MediaType.TEXT_MARKDOWN_VALUE,
                "# Test Document".getBytes()
        );

        DocumentResponse response = new DocumentResponse(
                1L, 1L, "test", "test.md", "PENDING", null, LocalDateTime.now(), 1, 1L
        );

        when(documentService.upload(any(), any(), any(), any())).thenReturn(response);

        mockMvc.perform(multipart("/api/v1/documents/upload")
                        .file(file)
                        .param("systemId", "1")
                        .param("securityLevel", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.originalFileName").value("test.md"));
    }

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void shouldRejectNonMarkdownFiles() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "plain text".getBytes()
        );

        // Service validates file type and throws exception
        when(documentService.upload(any(), any(), any(), any()))
                .thenThrow(new io.github.xiaoailazy.coexistree.shared.exception.BusinessException(
                        io.github.xiaoailazy.coexistree.shared.enums.ErrorCode.INVALID_FILE_TYPE, "Only markdown files are allowed"));

        mockMvc.perform(multipart("/api/v1/documents/upload")
                        .file(file)
                        .param("systemId", "1")
                        .param("securityLevel", "1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void shouldGetDocumentById() throws Exception {
        DocumentResponse response = new DocumentResponse(
                1L, 1L, "test", "test.md", "SUCCESS", null, LocalDateTime.now(), 1, 1L
        );

        when(documentService.getById(1L)).thenReturn(response);

        mockMvc.perform(get("/api/v1/documents/{id}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void shouldReturnErrorForNonExistentDocument() throws Exception {
        when(documentService.getById(999L))
                .thenThrow(new io.github.xiaoailazy.coexistree.shared.exception.BusinessException(
                        io.github.xiaoailazy.coexistree.shared.enums.ErrorCode.DOCUMENT_NOT_FOUND, "Document not found"));

        mockMvc.perform(get("/api/v1/documents/{id}", 999L))
                .andExpect(status().isBadRequest()); // Business exceptions return 400
    }

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void shouldListDocumentsBySystem() throws Exception {
        DocumentResponse doc1 = new DocumentResponse(
                1L, 1L, "doc1", "doc1.md", "SUCCESS", null, LocalDateTime.now(), 1, 1L
        );
        DocumentResponse doc2 = new DocumentResponse(
                2L, 1L, "doc2", "doc2.md", "PROCESSING", null, LocalDateTime.now(), 1, 1L
        );

        when(documentService.listBySystem(any(), any())).thenReturn(List.of(doc1, doc2));

        mockMvc.perform(get("/api/v1/documents").param("systemId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(2)));
    }

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void shouldDeleteDocument() throws Exception {
        doNothing().when(documentService).delete(1L);

        mockMvc.perform(delete("/api/v1/documents/{id}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void shouldProcessDocumentAsynchronously() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "async.md",
                MediaType.TEXT_MARKDOWN_VALUE,
                "# Async Test".getBytes()
        );

        DocumentResponse response = new DocumentResponse(
                1L, 1L, "async", "async.md", "PENDING", null, LocalDateTime.now(), 1, 1L
        );

        when(documentService.upload(any(), any(), any(), any())).thenReturn(response);

        mockMvc.perform(multipart("/api/v1/documents/upload")
                        .file(file)
                        .param("systemId", "1")
                        .param("securityLevel", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.parseStatus").value("PENDING"));
    }
}
