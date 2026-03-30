package io.github.xiaoailazy.coexistree.chat.service;

import io.github.xiaoailazy.coexistree.indexer.llm.LlmClient;
import io.github.xiaoailazy.coexistree.indexer.llm.PromptTemplateService;
import io.github.xiaoailazy.coexistree.indexer.model.NodeSource;
import io.github.xiaoailazy.coexistree.indexer.model.TreeNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class AnswerGenerationServiceImplTest {

    @Mock
    private PromptTemplateService promptTemplateService;

    @Mock
    private LlmClient llmClient;

    private AnswerGenerationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AnswerGenerationServiceImpl(promptTemplateService, llmClient);
    }

    @Test
    void testFormatNodes_withTextPopulated() throws Exception {
        // 准备测试数据：带原文的节点列表
        List<TreeNode> nodes = new ArrayList<>();

        TreeNode node1 = new TreeNode();
        node1.setNodeId("SYS_1");
        node1.setTitle("用户认证模块");
        node1.setText("用户认证模块负责处理用户登录、注册和权限验证。支持多种认证方式，包括用户名密码、OAuth2.0等。");
        nodes.add(node1);

        TreeNode node2 = new TreeNode();
        node2.setNodeId("SYS_2");
        node2.setTitle("数据存储层");
        node2.setText("数据存储层使用 PostgreSQL 数据库，通过 JPA 进行数据访问。支持事务管理和连接池配置。");
        nodes.add(node2);

        // 使用反射调用私有方法 formatNodes
        Method formatNodesMethod = AnswerGenerationServiceImpl.class.getDeclaredMethod("formatNodes", List.class);
        formatNodesMethod.setAccessible(true);
        String result = (String) formatNodesMethod.invoke(service, nodes);

        // 验证格式化结果
        String expected = "【用户认证模块】\n用户认证模块负责处理用户登录、注册和权限验证。支持多种认证方式，包括用户名密码、OAuth2.0等。\n\n" +
                "【数据存储层】\n数据存储层使用 PostgreSQL 数据库，通过 JPA 进行数据访问。支持事务管理和连接池配置。";

        assertEquals(expected, result);
    }

    @Test
    void testFormatNodes_withNullText() throws Exception {
        // 准备测试数据：text 为 null 的节点
        List<TreeNode> nodes = new ArrayList<>();

        TreeNode node = new TreeNode();
        node.setNodeId("SYS_1");
        node.setTitle("测试节点");
        node.setText(null);  // text 为 null
        nodes.add(node);

        // 使用反射调用私有方法 formatNodes
        Method formatNodesMethod = AnswerGenerationServiceImpl.class.getDeclaredMethod("formatNodes", List.class);
        formatNodesMethod.setAccessible(true);
        String result = (String) formatNodesMethod.invoke(service, nodes);

        // 验证：text 为 null 时应该使用空字符串
        String expected = "【测试节点】\n";
        assertEquals(expected, result);
    }

    @Test
    void testFormatNodes_emptyList() throws Exception {
        // 准备测试数据：空列表
        List<TreeNode> nodes = new ArrayList<>();

        // 使用反射调用私有方法 formatNodes
        Method formatNodesMethod = AnswerGenerationServiceImpl.class.getDeclaredMethod("formatNodes", List.class);
        formatNodesMethod.setAccessible(true);
        String result = (String) formatNodesMethod.invoke(service, nodes);

        // 验证：空列表应该返回空字符串
        assertEquals("", result);
    }

    @Test
    void testFormatNodes_multipleNodesWithSources() throws Exception {
        // 准备测试数据：带 sources 的节点（模拟系统知识树节点）
        List<TreeNode> nodes = new ArrayList<>();

        TreeNode node1 = new TreeNode();
        node1.setNodeId("SYS_1");
        node1.setTitle("API 接口");
        node1.setText("API 接口提供 RESTful 风格的 HTTP 接口。\n\n---\n\n支持 JSON 格式的请求和响应。");
        
        List<NodeSource> sources = new ArrayList<>();
        NodeSource source1 = new NodeSource();
        source1.setDocId(1L);
        source1.setNodeId("DOC1_NODE1");
        sources.add(source1);
        node1.setSources(sources);
        
        nodes.add(node1);

        // 使用反射调用私有方法 formatNodes
        Method formatNodesMethod = AnswerGenerationServiceImpl.class.getDeclaredMethod("formatNodes", List.class);
        formatNodesMethod.setAccessible(true);
        String result = (String) formatNodesMethod.invoke(service, nodes);

        // 验证：应该正确格式化包含多来源合并文本的节点
        String expected = "【API 接口】\nAPI 接口提供 RESTful 风格的 HTTP 接口。\n\n---\n\n支持 JSON 格式的请求和响应。";
        assertEquals(expected, result);
    }
}
