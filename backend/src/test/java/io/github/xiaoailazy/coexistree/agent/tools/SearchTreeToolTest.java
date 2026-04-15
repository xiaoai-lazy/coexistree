package io.github.xiaoailazy.coexistree.agent.tools;

import io.github.xiaoailazy.coexistree.agent.context.AgentUserContext;
import io.github.xiaoailazy.coexistree.knowledge.model.SystemKnowledgeTree;
import io.github.xiaoailazy.coexistree.knowledge.service.SystemKnowledgeTreeService;
import io.github.xiaoailazy.coexistree.indexer.model.TreeNode;
import io.github.xiaoailazy.coexistree.indexer.model.NodeSource;
import io.github.xiaoailazy.coexistree.indexer.model.TreeSearchResult;
import io.github.xiaoailazy.coexistree.chat.service.TreeSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SearchTreeToolTest {

    @Mock
    private SystemKnowledgeTreeService treeService;

    @Mock
    private TreeSearchService searchService;

    private SearchTreeTool tool;

    @BeforeEach
    void setUp() {
        tool = new SearchTreeTool(treeService, searchService);
    }

    @Test
    void shouldReturnSearchResults() {
        SystemKnowledgeTree tree = new SystemKnowledgeTree();
        TreeNode node = new TreeNode();
        node.setNodeId("n1");
        node.setTitle("认证模块");
        node.setLevel(1);
        NodeSource source = new NodeSource();
        source.setDocId(1L);
        source.setSecurityLevel(2);
        node.setSources(List.of(source));
        tree.setStructure(List.of(node));

        when(treeService.getActiveTree(2L)).thenReturn(tree);

        TreeSearchResult searchResult = new TreeSearchResult(
                null, "mock-thinking", List.of("n1"));
        when(searchService.search(anyList(), anyString(), any(), any()))
                .thenReturn(searchResult);

        AgentUserContext ctx = new AgentUserContext(1L, 2L, 3, "conv-1");

        String result = tool.execute(ctx, "用户认证如何实现");
        assertNotNull(result);
        assertTrue(result.contains("n1"));
    }

    @Test
    void shouldFilterNodesBySecurityLevel() {
        SystemKnowledgeTree tree = new SystemKnowledgeTree();

        TreeNode highNode = new TreeNode();
        highNode.setNodeId("n1");
        highNode.setTitle("高安全节点");
        highNode.setLevel(1);
        NodeSource hs = new NodeSource();
        hs.setDocId(1L);
        hs.setSecurityLevel(5);
        highNode.setSources(List.of(hs));

        TreeNode lowNode = new TreeNode();
        lowNode.setNodeId("n2");
        lowNode.setTitle("低安全节点");
        lowNode.setLevel(2);
        NodeSource ls = new NodeSource();
        ls.setDocId(1L);
        ls.setSecurityLevel(1);
        lowNode.setSources(List.of(ls));

        tree.setStructure(List.of(highNode, lowNode));

        when(treeService.getActiveTree(2L)).thenReturn(tree);

        TreeSearchResult searchResult = new TreeSearchResult(
                null, "mock-thinking", List.of("n1", "n2"));
        when(searchService.search(anyList(), anyString(), any(), any()))
                .thenReturn(searchResult);

        AgentUserContext ctx = new AgentUserContext(1L, 2L, 2, "conv-1");

        String result = tool.execute(ctx, "测试");
        // n1 应该被过滤掉（securityLevel=5 > viewLevel=2）
        assertFalse(result.contains("n1"));
        assertTrue(result.contains("n2"));
    }
}
