package io.github.xiaoailazy.coexistree.agent.tools;

import com.google.adk.sessions.State;
import com.google.adk.tools.ToolContext;
import io.github.xiaoailazy.coexistree.document.service.DocumentTreeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReadNodeTextToolTest {

    @Mock
    private DocumentTreeService documentTreeService;

    @Mock
    private ToolContext toolContext;

    @Mock
    private State state;

    private ReadNodeTextTool tool;

    @BeforeEach
    void setUp() {
        lenient().when(toolContext.state()).thenReturn(state);
        tool = new ReadNodeTextTool(documentTreeService);
    }

    @Test
    void shouldReadMultipleNodes() {
        lenient().when(state.entrySet()).thenReturn(java.util.Map.<String, Object>of("user:readableDocIds", java.util.List.of(1L)).entrySet());
        lenient().when(state.get("user:readableDocIds")).thenReturn(java.util.List.of(1L));
        when(documentTreeService.getNodeText(1L, "n1"))
                .thenReturn("节点1内容");
        when(documentTreeService.getNodeText(1L, "n2"))
                .thenReturn("节点2内容");

        List<ReadNodeTextTool.NodeRef> nodes = List.of(
                new ReadNodeTextTool.NodeRef(1L, "n1"),
                new ReadNodeTextTool.NodeRef(1L, "n2")
        );
        String result = tool.readNodeTexts(nodes, toolContext);

        assertTrue(result.contains("节点1内容"));
        assertTrue(result.contains("节点2内容"));
        assertTrue(result.contains("[n1]"));
        assertTrue(result.contains("[n2]"));
    }

    @Test
    void shouldSkipUnreadableNodes() {
        when(toolContext.state()).thenReturn(state);
        when(state.entrySet()).thenReturn(java.util.Map.<String, Object>of("user:readableDocIds", java.util.List.of(1L)).entrySet());
        when(state.get("user:readableDocIds")).thenReturn(java.util.List.of(1L));

        List<ReadNodeTextTool.NodeRef> nodes = List.of(
                new ReadNodeTextTool.NodeRef(1L, "n1"),
                new ReadNodeTextTool.NodeRef(2L, "n2")
        );
        when(documentTreeService.getNodeText(1L, "n1")).thenReturn("节点1内容");

        String result = tool.readNodeTexts(nodes, toolContext);

        assertTrue(result.contains("节点1内容"));
        assertTrue(result.contains("[n2] 无权限访问此节点"));
    }

    @Test
    void shouldHandleEmptyNode() {
        lenient().when(state.entrySet()).thenReturn(java.util.Map.<String, Object>of("user:readableDocIds", java.util.List.of(1L)).entrySet());
        lenient().when(state.get("user:readableDocIds")).thenReturn(java.util.List.of(1L));
        when(documentTreeService.getNodeText(1L, "n1"))
                .thenReturn("");

        List<ReadNodeTextTool.NodeRef> nodes = List.of(
                new ReadNodeTextTool.NodeRef(1L, "n1")
        );
        String result = tool.readNodeTexts(nodes, toolContext);
        assertTrue(result.contains("无可用原文"));
    }

    @Test
    void shouldHandleEmptyList() {
        String result = tool.readNodeTexts(List.of(), toolContext);
        assertTrue(result.contains("未指定"));
    }

    @Test
    void shouldHandleNodeWithNullText() {
        lenient().when(state.entrySet()).thenReturn(java.util.Map.<String, Object>of("user:readableDocIds", java.util.List.of(1L)).entrySet());
        lenient().when(state.get("user:readableDocIds")).thenReturn(java.util.List.of(1L));
        when(documentTreeService.getNodeText(1L, "n1"))
                .thenReturn(null);

        List<ReadNodeTextTool.NodeRef> nodes = List.of(
                new ReadNodeTextTool.NodeRef(1L, "n1")
        );
        String result = tool.readNodeTexts(nodes, toolContext);
        assertTrue(result.contains("无可用原文"));
    }
}
