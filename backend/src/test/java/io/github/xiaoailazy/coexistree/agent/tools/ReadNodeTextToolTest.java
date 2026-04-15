package io.github.xiaoailazy.coexistree.agent.tools;

import io.github.xiaoailazy.coexistree.document.service.DocumentTreeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReadNodeTextToolTest {

    @Mock
    private DocumentTreeService documentTreeService;

    private ReadNodeTextTool tool;

    @BeforeEach
    void setUp() {
        tool = new ReadNodeTextTool(documentTreeService);
    }

    @Test
    void shouldReturnNodeText() {
        when(documentTreeService.getNodeText(1L, "n1"))
                .thenReturn("这是节点原文");

        String result = tool.execute(1L, "n1");
        assertEquals("这是节点原文", result);
    }

    @Test
    void shouldReturnEmptyMessageWhenNoText() {
        when(documentTreeService.getNodeText(1L, "n1"))
                .thenReturn("");

        String result = tool.execute(1L, "n1");
        assertTrue(result.contains("无可用原文"));
    }
}
