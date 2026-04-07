package io.github.xiaoailazy.coexistree.indexer.model;

import java.util.List;

public record Citation(
        String nodeId,
        String title,
        String snippet,
        List<NodeSource> sources,
        // New fields
        Long docId,
        String docName,
        Integer lineNum,
        Integer level
) {
    // Backward compatible constructor
    public Citation(String nodeId, String title, String snippet, List<NodeSource> sources) {
        this(nodeId, title, snippet, sources, null, null, null, null);
    }
}

