package io.github.xiaoailazy.coexistree.chat.service;

import io.github.xiaoailazy.coexistree.indexer.llm.LlmClient;
import io.github.xiaoailazy.coexistree.indexer.llm.PromptTemplateService;
import io.github.xiaoailazy.coexistree.indexer.model.TreeNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AnswerGenerationServiceImpl implements AnswerGenerationService {

    private final PromptTemplateService promptTemplateService;
    private final LlmClient llmClient;

    public AnswerGenerationServiceImpl(PromptTemplateService promptTemplateService, LlmClient llmClient) {
        this.promptTemplateService = promptTemplateService;
        this.llmClient = llmClient;
    }

    @Override
    public String generate(String query, List<TreeNode> relevantNodes, String model, String previousResponseId) {
        log.debug("开始生成答案, query={}, 相关节点数量={}", query, relevantNodes.size());
        String prompt = promptTemplateService.buildAnswerPrompt(query, formatNodes(relevantNodes));
        LlmClient.LlmResponse response = llmClient.chat(prompt, model, null, previousResponseId);
        log.debug("答案生成完成, 答案长度={}", response.content().length());
        return response.content();
    }

    @Override
    public String generateStream(String query, List<TreeNode> relevantNodes, String model, String previousResponseId,
                                 Consumer<String> onThinking, Consumer<String> onText) {
        log.debug("开始流式生成答案, query={}, 相关节点数量={}", query, relevantNodes.size());
        String prompt = promptTemplateService.buildAnswerPrompt(query, formatNodes(relevantNodes));
        return llmClient.chatStream(prompt, model, null, previousResponseId, onThinking, onText);
    }

    /**
     * 格式化节点列表为 LLM 输入格式（标题 + 原文）
     * <p>
     * 注意：此方法期望接收的节点已经通过 ConversationServiceImpl 填充了 text 字段（原文）。
     * text 字段包含从文档树节点获取的原始文本内容。
     *
     * @param nodes 带原文的节点列表
     * @return 格式化后的字符串，格式为：【标题】\n原文\n\n【标题】\n原文...
     */
    private String formatNodes(List<TreeNode> nodes) {
        return nodes.stream()
                .map(node -> "【" + node.getTitle() + "】\n" + (node.getText() == null ? "" : node.getText()))
                .collect(Collectors.joining("\n\n"));
    }
}
