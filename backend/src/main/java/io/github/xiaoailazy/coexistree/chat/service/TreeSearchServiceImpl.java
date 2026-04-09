package io.github.xiaoailazy.coexistree.chat.service;

import io.github.xiaoailazy.coexistree.indexer.llm.LlmClient;
import io.github.xiaoailazy.coexistree.indexer.llm.LlmResponseParser;
import io.github.xiaoailazy.coexistree.indexer.llm.PromptTemplateService;
import io.github.xiaoailazy.coexistree.indexer.llm.RetryableLlmService;
import io.github.xiaoailazy.coexistree.indexer.model.TreeNode;
import io.github.xiaoailazy.coexistree.indexer.model.TreeSearchResult;
import io.github.xiaoailazy.coexistree.indexer.tree.TreeSanitizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class TreeSearchServiceImpl implements TreeSearchService {

    private final PromptTemplateService promptTemplateService;
    private final RetryableLlmService retryableLlmService;
    private final TreeSanitizer treeSanitizer;

    public TreeSearchServiceImpl(
            PromptTemplateService promptTemplateService,

            RetryableLlmService retryableLlmService,
            TreeSanitizer treeSanitizer
    ) {
        this.promptTemplateService = promptTemplateService;
        this.retryableLlmService = retryableLlmService;
        this.treeSanitizer = treeSanitizer;
    }

    @Override
    public TreeSearchResult search(List<TreeNode> structure, String query, String model) {
        return search(structure, query, model, null);
    }

    @Override
    public TreeSearchResult search(List<TreeNode> structure, String query, String model, String previousResponseId) {
        log.debug("开始树搜索, query={}, 节点数量={}, previousResponseId={}", query, structure.size(), previousResponseId);

        List<TreeNode> treeWithoutText = treeSanitizer.removeText(structure);

        String prompt = promptTemplateService.buildTreeSearchPrompt(query, treeWithoutText);
        log.debug("生成树搜索Prompt, prompt长度={}", prompt.length());

        // 使用带重试的服务
        RetryableLlmService.TreeSearchResultWithResponseId wrappedResult =
                retryableLlmService.treeSearchWithResponseId(prompt, model, 0.0);

        TreeSearchResult result = wrappedResult.result();
        log.debug("LLM响应长度={}, responseId={}",
                result.getThinking() != null ? result.getThinking().length() : 0,
                wrappedResult.responseId());

        log.debug("树搜索完成, 找到{}个相关节点, thinking长度={}, responseId={}",
                result.getNodeList().size(),
                result.getThinking() != null ? result.getThinking().length() : 0,
                result.getResponseId());

        return result;
    }
}
