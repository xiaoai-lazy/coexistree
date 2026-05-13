package io.github.xiaoailazy.coexistree.indexer.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Simplified tree node containing only fields relevant to LLM prompts.
 * Excludes text, lineNum, sources, provenance and other internal metadata.
 */
@Data
public class SimplifiedTreeNode {
    private String nodeId;
    private String title;
    private String summary;
    private String prefixSummary;
    private List<SimplifiedTreeNode> children = new ArrayList<>();
}
