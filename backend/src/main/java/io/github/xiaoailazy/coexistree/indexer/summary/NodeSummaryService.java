package io.github.xiaoailazy.coexistree.indexer.summary;

import io.github.xiaoailazy.coexistree.indexer.summary.SummaryLengthPolicy.SummaryLengthConfig;
import io.github.xiaoailazy.coexistree.indexer.llm.LlmClient;
import org.springframework.stereotype.Service;

@Service
public class NodeSummaryService {

    private final LlmClient llmClient;

    public NodeSummaryService(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    /**
     * 根据层级动态长度生成摘要
     *
     * @param text 节点文本内容
     * @param level 节点层级 (1-5)
     * @param model LLM 模型
     * @return 摘要文本
     */
    public String summarizeNodeText(String text, int level, String model) {
        SummaryLengthConfig config = SummaryLengthPolicy.getConfig(level);
        String normalized = normalize(text);
        if (normalized.isEmpty()) {
            return "";
        }

        // 如果文本长度小于最小值，直接返回
        if (countTokens(normalized) < config.getMinLength()) {
            return normalized;
        }

        // 调用 LLM 生成摘要
        String prompt = buildPrompt(normalized, config);
        return llmClient.chat(prompt, model, 0.0).content();
    }


    /**
     * 构建带长度限制的 Prompt
     */
    private String buildPrompt(String text, SummaryLengthConfig config) {
        return """
                你需要为以下技术文档节点生成摘要，用于后续的检索匹配。

                要求:
                1. 长度控制在 %d-%d 字之间
                2. 包含该节点的核心功能/概念
                3. 列出关键的依赖或关联内容
                4. 使用清晰的技术术语，避免模糊描述
                5. 保持语气客观，不添加原文没有的推断

                原文:
                %s

                输出格式:
                摘要: [生成的摘要内容]
                """.formatted(config.getMinLength(), config.getMaxLength(), text);
    }

    private String buildPrompt(String text) {
        return """
                You are given a part of a document. Your task is to generate a concise description of the main points covered in this partial document.

                Partial Document Text:
                %s

                Directly return the description, do not include any other text.
                """.formatted(text);
    }

    private String normalize(String text) {
        return text == null ? "" : text.replaceAll("\\s+", " ").trim();
    }

    private int countTokens(String text) {
        if (text.isEmpty()) {
            return 0;
        }
        return text.split("\\s+").length;
    }
}
