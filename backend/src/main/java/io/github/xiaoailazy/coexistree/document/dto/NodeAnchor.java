package io.github.xiaoailazy.coexistree.document.dto;

public record NodeAnchor(
        String nodeId,
        String title,
        Integer lineNum,
        Integer level
) {}
