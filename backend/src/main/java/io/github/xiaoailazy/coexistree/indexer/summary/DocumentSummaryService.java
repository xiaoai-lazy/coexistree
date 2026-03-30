package io.github.xiaoailazy.coexistree.indexer.summary;

import io.github.xiaoailazy.coexistree.shared.util.JsonUtils;
import io.github.xiaoailazy.coexistree.indexer.llm.LlmClient;
import io.github.xiaoailazy.coexistree.indexer.model.TreeNode;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DocumentSummaryService {

    private final LlmClient llmClient;
    private final JsonUtils jsonUtils;

    public DocumentSummaryService(LlmClient llmClient, JsonUtils jsonUtils) {
        this.llmClient = llmClient;
        this.jsonUtils = jsonUtils;
    }

    public String summarize(List<TreeNode> structure, String docName, String model) {
        String prompt = """
                You are an expert in generating descriptions for a document.
                You are given a structure of a document. Your task is to generate a one-sentence description for the document, which makes it easy to distinguish the document from other documents.

                Document Structure: %s

                Directly return the description, do not include any other text.
                """.formatted(jsonUtils.toJson(structure));
        return llmClient.chat(prompt, model, 0.0).content();
    }
}
