package io.github.xiaoailazy.coexistree.indexer.model;

public record PageIndexBuildOptions(
        boolean thinning,
        int minTokenThreshold,
        boolean addNodeSummary,
        int summaryTokenThreshold,
        boolean addDocDescription,
        boolean addNodeText,
        boolean addNodeId,
        String model
) {
    public static PageIndexBuildOptions defaultOptions(String model) {
        return new PageIndexBuildOptions(false, 5000, true, 200, true, true, true, model);
    }
}

