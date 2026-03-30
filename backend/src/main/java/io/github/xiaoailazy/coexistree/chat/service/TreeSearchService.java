package io.github.xiaoailazy.coexistree.chat.service;

import io.github.xiaoailazy.coexistree.indexer.model.TreeNode;
import io.github.xiaoailazy.coexistree.indexer.model.TreeSearchResult;

import java.util.List;

public interface TreeSearchService {

    TreeSearchResult search(List<TreeNode> structure, String query, String model);

    TreeSearchResult search(List<TreeNode> structure, String query, String model, String previousResponseId);
}
