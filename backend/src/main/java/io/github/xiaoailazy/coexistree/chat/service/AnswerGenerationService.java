package io.github.xiaoailazy.coexistree.chat.service;

import io.github.xiaoailazy.coexistree.indexer.model.TreeNode;

import java.util.List;
import java.util.function.Consumer;

public interface AnswerGenerationService {

    String generate(String query, List<TreeNode> relevantNodes, String model, String previousResponseId);

    String generateStream(String query, List<TreeNode> relevantNodes, String model, String previousResponseId,
                          Consumer<String> onThinking, Consumer<String> onText);
}
