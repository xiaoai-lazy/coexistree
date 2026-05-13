package io.github.xiaoailazy.coexistree.agent.tools;

import com.google.adk.sessions.State;
import com.google.adk.tools.ToolContext;
import io.github.xiaoailazy.coexistree.document.entity.DocumentEntity;
import io.github.xiaoailazy.coexistree.document.repository.DocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReadDocumentToolTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private ToolContext toolContext;

    @Mock
    private State state;

    private ReadDocumentTool tool;

    @BeforeEach
    void setUp() {
        lenient().when(toolContext.state()).thenReturn(state);
        tool = new ReadDocumentTool(documentRepository);
    }

    @Test
    void shouldReturnDocumentContent() {
        lenient().when(state.entrySet()).thenReturn(java.util.Map.<String, Object>of("user:readableDocIds", java.util.List.of(1L)).entrySet());
        lenient().when(state.get("user:readableDocIds")).thenReturn(java.util.List.of(1L));
        DocumentEntity doc = new DocumentEntity();
        doc.setId(1L);
        doc.setDocName("test.md");
        doc.setFileContent("文档内容");
        doc.setSecurityLevel(2);
        when(documentRepository.findById(1L)).thenReturn(Optional.of(doc));

        String result = tool.readDocument(1L, toolContext);
        assertEquals("文档内容", result);
    }

    @Test
    void shouldDenyUnreadableDocument() {
        lenient().when(state.entrySet()).thenReturn(java.util.Map.<String, Object>of("user:readableDocIds", java.util.List.of(1L)).entrySet());
        lenient().when(state.get("user:readableDocIds")).thenReturn(java.util.List.of(1L));

        String result = tool.readDocument(2L, toolContext);

        assertTrue(result.contains("无权限访问此文档"));
    }
}
