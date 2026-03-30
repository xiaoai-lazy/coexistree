package io.github.xiaoailazy.coexistree.shared.util;

import java.nio.file.Path;

public final class FilePathUtils {

    private FilePathUtils() {
    }

    public static Path markdownPath(String docRoot, String systemCode, Long documentId) {
        return Path.of(docRoot, systemCode, documentId + ".md");
    }

    public static Path treePath(String treeRoot, String systemCode, Long documentId) {
        return Path.of(treeRoot, systemCode, documentId + "_tree.json");
    }

    public static Path systemTreePath(String systemTreeRoot, String systemCode) {
        return Path.of(systemTreeRoot, systemCode, "system_tree.json");
    }
}

