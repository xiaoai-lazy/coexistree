package io.github.xiaoailazy.coexistree.shared.test;

import io.github.xiaoailazy.coexistree.indexer.llm.LlmClient;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * LLM 调用 Mock 工厂类 - 统一管理系统中所有 LLM 调用的 mock 数据
 *
 * <p>本类提供预定义的 mock 响应，用于各种 LLM 调用场景，确保测试不会调用真实 LLM API。
 *
 * <p>使用示例：
 * <pre>
 * // 基础用法 - 使用默认成功响应
 * LlmMockFactory.mockChat(llmClient);
 *
 * // 指定具体响应内容
 * LlmMockFactory.mockChat(llmClient, "自定义响应内容");
 *
 * // 使用场景特定的 mock
 * LlmMockFactory.mockForTitleGeneration(llmClient, "生成的标题");
 *
 * // 链式调用 - 模拟多次调用返回不同结果
 * LlmMockFactory.builder(llmClient)
 *     .respondWith("第一次调用结果")
 *     .respondWith("第二次调用结果")
 *     .build();
 * </pre>
 *
 * @see LlmClient
 */
public class LlmMockFactory {

    private LlmMockFactory() {
        // 工具类，禁止实例化
    }

    // ==================== 基础 Mock 方法 ====================

    /**
     * Mock LLM 调用 - 使用默认成功响应
     *
     * @param llmClient mock 的 LlmClient 实例
     */
    public static void mockChat(LlmClient llmClient) {
        mockChat(llmClient, "mock-response", null);
    }

    /**
     * Mock LLM 调用 - 返回指定内容
     *
     * @param llmClient mock 的 LlmClient 实例
     * @param content   返回的响应内容
     */
    public static void mockChat(LlmClient llmClient, String content) {
        mockChat(llmClient, content, null);
    }

    /**
     * Mock LLM 调用 - 返回指定内容和 responseId
     *
     * @param llmClient  mock 的 LlmClient 实例
     * @param content    返回的响应内容
     * @param responseId 返回的响应 ID
     */
    public static void mockChat(LlmClient llmClient, String content, String responseId) {
        // 使用 lenient() 避免 UnnecessaryStubbingException，因为测试可能只使用其中一个方法签名
        lenient().when(llmClient.chat(anyString(), any(), anyDouble()))
                .thenReturn(new LlmClient.LlmResponse(responseId, content));
        lenient().when(llmClient.chat(anyString(), any(), anyDouble(), any()))
                .thenReturn(new LlmClient.LlmResponse(responseId, content));
    }

    /**
     * Mock LLM 调用 - 抛出异常
     *
     * @param llmClient mock 的 LlmClient 实例
     * @param exception 要抛出的异常
     */
    public static void mockChatException(LlmClient llmClient, RuntimeException exception) {
        lenient().when(llmClient.chat(anyString(), any(), anyDouble())).thenThrow(exception);
        lenient().when(llmClient.chat(anyString(), any(), anyDouble(), any())).thenThrow(exception);
    }

    // ==================== 场景特定的 Mock 方法 ====================

    /**
     * Mock 标题生成场景
     *
     * @param llmClient mock 的 LlmClient 实例
     * @param title     要返回的标题
     */
    public static void mockForTitleGeneration(LlmClient llmClient, String title) {
        mockChat(llmClient, title, null);
    }

    /**
     * Mock 节点摘要生成场景
     *
     * @param llmClient mock 的 LlmClient 实例
     * @param summary   要返回的摘要
     */
    public static void mockForNodeSummary(LlmClient llmClient, String summary) {
        mockChat(llmClient, summary, null);
    }

    /**
     * Mock 文档摘要生成场景
     *
     * @param llmClient mock 的 LlmClient 实例
     * @param summary   要返回的摘要
     */
    public static void mockForDocumentSummary(LlmClient llmClient, String summary) {
        mockChat(llmClient, summary, null);
    }

    /**
     * Mock 树搜索场景 - 返回节点 ID 列表 JSON 格式
     *
     * @param llmClient mock 的 LlmClient 实例
     * @param nodeIds   节点 ID 数组
     */
    public static void mockForTreeSearch(LlmClient llmClient, String... nodeIds) {
        String json = toJsonArray(nodeIds);
        String response = """
                {
                    "nodeList": %s,
                    "thinking": "思考过程：根据查询匹配到相关节点"
                }
                """.formatted(json);
        mockChat(llmClient, response, "resp_" + System.currentTimeMillis());
    }

    /**
     * Mock 树搜索场景 - 返回空结果
     *
     * @param llmClient mock 的 LlmClient 实例
     */
    public static void mockForTreeSearchEmpty(LlmClient llmClient) {
        mockForTreeSearch(llmClient, new String[0]);
    }

    /**
     * Mock 意图分类场景 - 问答意图
     *
     * @param llmClient mock 的 LlmClient 实例
     */
    public static void mockForIntentClassificationQuestion(LlmClient llmClient) {
        String response = """
                {
                    "intent": "QUESTION",
                    "confidence": "HIGH",
                    "reason": "用户询问系统现有功能",
                    "suggestedAction": null
                }
                """;
        mockChat(llmClient, response, null);
    }

    /**
     * Mock 意图分类场景 - 需求评估意图
     *
     * @param llmClient mock 的 LlmClient 实例
     */
    public static void mockForIntentClassificationEval(LlmClient llmClient) {
        String response = """
                {
                    "intent": "REQUIREMENT_EVAL",
                    "confidence": "HIGH",
                    "reason": "用户想评估需求影响",
                    "suggestedAction": null
                }
                """;
        mockChat(llmClient, response, null);
    }

    /**
     * Mock 冲突检测场景
     *
     * @param llmClient mock 的 LlmClient 实例
     * @param riskLevel 风险等级：HIGH/MEDIUM/LOW/NONE
     */
    public static void mockForConflictDetection(LlmClient llmClient, String riskLevel) {
        String response = """
                {
                    "riskLevel": "%s",
                    "summary": "冲突检测摘要",
                    "items": [
                        {
                            "name": "示例冲突",
                            "description": "这是一个示例冲突描述",
                            "severity": "medium",
                            "suggestion": "建议解决方案"
                        }
                    ]
                }
                """.formatted(riskLevel);
        mockChat(llmClient, response, "resp_conflict_" + System.currentTimeMillis());
    }

    /**
     * Mock 冲突检测场景 - 无冲突
     *
     * @param llmClient mock 的 LlmClient 实例
     */
    public static void mockForConflictDetectionNoConflict(LlmClient llmClient) {
        String response = """
                {
                    "riskLevel": "NONE",
                    "summary": "未发现功能冲突",
                    "items": []
                }
                """;
        mockChat(llmClient, response, "resp_no_conflict");
    }

    /**
     * Mock 规则一致性检查场景
     *
     * @param llmClient mock 的 LlmClient 实例
     * @param passed    是否通过
     */
    public static void mockForRuleConsistencyCheck(LlmClient llmClient, boolean passed) {
        String riskLevel = passed ? "NONE" : "MEDIUM";
        String response = """
                {
                    "riskLevel": "%s",
                    "summary": "%s",
                    "items": []
                }
                """.formatted(riskLevel, passed ? "规则检查通过" : "发现规则不一致");
        mockChat(llmClient, response, null);
    }

    /**
     * Mock 历史一致性检查场景
     *
     * @param llmClient mock 的 LlmClient 实例
     * @param passed    是否通过
     */
    public static void mockForHistoryConsistencyCheck(LlmClient llmClient, boolean passed) {
        mockForRuleConsistencyCheck(llmClient, passed);
    }

    /**
     * Mock 模块影响分析场景
     *
     * @param llmClient mock 的 LlmClient 实例
     */
    public static void mockForModuleImpactAnalysis(LlmClient llmClient) {
        String response = """
                {
                    "riskLevel": "MEDIUM",
                    "summary": "需求可能影响以下模块",
                    "items": [
                        {
                            "name": "用户模块",
                            "description": "需求涉及用户相关功能",
                            "severity": "medium",
                            "suggestion": "需要关注用户权限变化"
                        }
                    ]
                }
                """;
        mockChat(llmClient, response, "resp_impact_" + System.currentTimeMillis());
    }

    /**
     * Mock 答案生成场景
     *
     * @param llmClient mock 的 LlmClient 实例
     * @param answer    要返回的答案
     */
    public static void mockForAnswerGeneration(LlmClient llmClient, String answer) {
        mockChat(llmClient, answer, "resp_answer_" + System.currentTimeMillis());
    }

    // ==================== 构建器模式 ====================

    /**
     * 创建链式 Mock 构建器
     *
     * <p>用于需要多次调用返回不同结果的场景
     *
     * @param llmClient mock 的 LlmClient 实例
     * @return MockBuilder 实例
     */
    public static MockBuilder builder(LlmClient llmClient) {
        return new MockBuilder(llmClient);
    }

    /**
     * LLM Mock 构建器 - 支持链式调用设置多次返回值
     */
    public static class MockBuilder {
        private final LlmClient llmClient;
        private final org.mockito.stubbing.OngoingStubbing<LlmClient.LlmResponse> stubbing;
        private final org.mockito.stubbing.OngoingStubbing<LlmClient.LlmResponse> stubbingWithPreviousId;

        private MockBuilder(LlmClient llmClient) {
            this.llmClient = llmClient;
            this.stubbing = when(llmClient.chat(anyString(), any(), anyDouble()));
            this.stubbingWithPreviousId = when(llmClient.chat(anyString(), any(), anyDouble(), any()));
        }

        /**
         * 添加一次调用的响应
         *
         * @param content 响应内容
         * @return 构建器自身，支持链式调用
         */
        public MockBuilder respondWith(String content) {
            return respondWith(content, null);
        }

        /**
         * 添加一次调用的响应
         *
         * @param content    响应内容
         * @param responseId 响应 ID
         * @return 构建器自身，支持链式调用
         */
        public MockBuilder respondWith(String content, String responseId) {
            stubbing.thenReturn(new LlmClient.LlmResponse(responseId, content));
            stubbingWithPreviousId.thenReturn(new LlmClient.LlmResponse(responseId, content));
            return this;
        }

        /**
         * 完成构建
         */
        public void build() {
            // 构建器模式结束标记，无实际操作
        }
    }

    // ==================== 辅助方法 ====================

    private static String toJsonArray(String[] items) {
        if (items == null || items.length == 0) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < items.length; i++) {
            sb.append("\"").append(items[i]).append("\"");
            if (i < items.length - 1) {
                sb.append(", ");
            }
        }
        sb.append("]");
        return sb.toString();
    }
}
