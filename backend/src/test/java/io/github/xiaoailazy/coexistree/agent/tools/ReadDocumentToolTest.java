package io.github.xiaoailazy.coexistree.agent.tools;

import io.github.xiaoailazy.coexistree.document.entity.DocumentEntity;
import io.github.xiaoailazy.coexistree.document.repository.DocumentRepository;
import io.github.xiaoailazy.coexistree.document.storage.MarkdownFileStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReadDocumentToolTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private MarkdownFileStorageService storageService;

    private ReadDocumentTool tool;

    @BeforeEach
    void setUp() {
        tool = new ReadDocumentTool(documentRepository, storageService);
    }

    @Test
    void shouldReturnDocumentContent() {
        DocumentEntity doc = new DocumentEntity();
        doc.setId(1L);
        doc.setDocName("test.md");
        doc.setFilePath("test.md");
        doc.setSecurityLevel(2);
        when(documentRepository.findById(1L)).thenReturn(Optional.of(doc));
        when(storageService.read(any(Path.class))).thenReturn("文档内容");

        String result = tool.execute(1L);
        assertEquals("文档内容", result);
    }

    @Test
    void shouldReturnNotFoundForNonExistentDocument() {
        when(documentRepository.findById(999L)).thenReturn(Optional.empty());

        String result = tool.execute(999L);
        assertTrue(result.contains("不存在"));
    }
}
