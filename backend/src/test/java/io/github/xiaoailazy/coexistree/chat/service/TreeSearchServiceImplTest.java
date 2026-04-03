package io.github.xiaoailazy.coexistree.chat.service;

import io.github.xiaoailazy.coexistree.indexer.llm.LlmClient;
import io.github.xiaoailazy.coexistree.indexer.llm.LlmResponseParser;
import io.github.xiaoailazy.coexistree.shared.test.LlmMockFactory;
import io.github.xiaoailazy.coexistree.indexer.llm.PromptTemplateService;
import io.github.xiaoailazy.coexistree.indexer.model.TreeNode;
import io.github.xiaoailazy.coexistree.indexer.model.TreeSearchResult;
import io.github.xiaoailazy.coexistree.indexer.tree.TreeSanitizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TreeSearchServiceImplTest {

    @Mock
    private PromptTemplateService promptTemplateService;
    @Mock
    private LlmClient llmClient;
    @Mock
    private LlmResponseParser llmResponseParser;
    @Mock
    private TreeSanitizer treeSanitizer;

    private TreeSearchServiceImpl treeSearchService;

    @BeforeEach
    void setUp() {
        treeSearchService = new TreeSearchServiceImpl(
                promptTemplateService,
                llmClient,
                llmResponseParser,
                treeSanitizer
        );
    }

    @Test
    void testSearchWithAllParameters() {
        // Given
        TreeNode node1 = createTreeNode("1", "标题1", "内容1");
        TreeNode node2 = createTreeNode("2", "标题2", "内容2");
        List<TreeNode> structure = List.of(node1, node2);
        String query = "测试查询";
        String model = "test-model";
        String previousResponseId = "prev_123";

        List<TreeNode> sanitizedStructure = List.of(
                createTreeNode("1", "标题1", null),
                createTreeNode("2", "标题2", null)
        );

        String prompt = "生成的prompt";
        String llmResponse = "[1,2]";
        TreeSearchResult expectedResult = new TreeSearchResult();
        expectedResult.setNodeList(List.of("1", "2"));
        expectedResult.setThinking("思考过程");

        when(treeSanitizer.removeText(structure)).thenReturn(sanitizedStructure);
        when(promptTemplateService.buildTreeSearchPrompt(query, sanitizedStructure)).thenReturn(prompt);
        LlmMockFactory.mockChat(llmClient, llmResponse, "resp_456");
        when(llmResponseParser.parseTreeSearch(llmResponse)).thenReturn(expectedResult);

        // When
        TreeSearchResult result = treeSearchService.search(structure, query, model, previousResponseId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getNodeList()).containsExactly("1", "2");
        assertThat(result.getResponseId()).isEqualTo("resp_456");
        assertThat(result.getThinking()).isEqualTo("思考过程");
    }

    @Test
    void testSearchWithNullModel() {
        // Given
        TreeNode node = createTreeNode("1", "标题", "内容");
        List<TreeNode> structure = List.of(node);
        String query = "查询";

        List<TreeNode> sanitizedStructure = List.of(createTreeNode("1", "标题", null));

        String prompt = "prompt";
        String llmResponse = "[1]";
        TreeSearchResult expectedResult = new TreeSearchResult();
        expectedResult.setNodeList(List.of("1"));

        when(treeSanitizer.removeText(structure)).thenReturn(sanitizedStructure);
        when(promptTemplateService.buildTreeSearchPrompt(query, sanitizedStructure)).thenReturn(prompt);
        LlmMockFactory.mockChat(llmClient, llmResponse, null);
        when(llmResponseParser.parseTreeSearch(llmResponse)).thenReturn(expectedResult);

        // When
        TreeSearchResult result = treeSearchService.search(structure, query, null);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getNodeList()).containsExactly("1");
    }

    @Test
    void testSearchWithEmptyStructure() {
        // Given
        List<TreeNode> structure = List.of();
        String query = "查询";

        List<TreeNode> sanitizedStructure = List.of();

        String prompt = "prompt";
        String llmResponse = "[]";
        TreeSearchResult expectedResult = new TreeSearchResult();
        expectedResult.setNodeList(List.of());

        when(treeSanitizer.removeText(structure)).thenReturn(sanitizedStructure);
        when(promptTemplateService.buildTreeSearchPrompt(query, sanitizedStructure)).thenReturn(prompt);
        LlmMockFactory.mockChat(llmClient, llmResponse, null);
        when(llmResponseParser.parseTreeSearch(llmResponse)).thenReturn(expectedResult);

        // When
        TreeSearchResult result = treeSearchService.search(structure, query, null);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getNodeList()).isEmpty();
    }

    @Test
    void testSearchWithNestedStructure() {
        // Given
        TreeNode childNode = createTreeNode("1.1", "子标题", "子内容");
        TreeNode parentNode = createTreeNode("1", "父标题", "父内容", List.of(childNode));
        List<TreeNode> structure = List.of(parentNode);

        TreeNode sanitizedChild = createTreeNode("1.1", "子标题", null);
        TreeNode sanitizedParent = createTreeNode("1", "父标题", null, List.of(sanitizedChild));
        List<TreeNode> sanitizedStructure = List.of(sanitizedParent);

        String prompt = "prompt";
        String llmResponse = "[1,1.1]";
        TreeSearchResult expectedResult = new TreeSearchResult();
        expectedResult.setNodeList(List.of("1", "1.1"));

        when(treeSanitizer.removeText(structure)).thenReturn(sanitizedStructure);
        when(promptTemplateService.buildTreeSearchPrompt(anyString(), anyList())).thenReturn(prompt);
        LlmMockFactory.mockChat(llmClient, llmResponse, null);
        when(llmResponseParser.parseTreeSearch(llmResponse)).thenReturn(expectedResult);

        // When
        TreeSearchResult result = treeSearchService.search(structure, "查询", null);

        // Then
        assertThat(result.getNodeList()).containsExactly("1", "1.1");
    }

    private TreeNode createTreeNode(String nodeId, String title, String text) {
        TreeNode node = new TreeNode();
        node.setNodeId(nodeId);
        node.setTitle(title);
        node.setText(text);
        node.setLevel(1);
        return node;
    }

    private TreeNode createTreeNode(String nodeId, String title, String text, List<TreeNode> children) {
        TreeNode node = createTreeNode(nodeId, title, text);
        node.setNodes(children);
        return node;
    }
}
