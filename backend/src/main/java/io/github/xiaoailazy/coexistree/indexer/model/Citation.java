package io.github.xiaoailazy.coexistree.indexer.model;

import java.util.List;

public record Citation(
        String nodeId,
        String title,
        String snippet,
        List<NodeSource> sources
) {
}

