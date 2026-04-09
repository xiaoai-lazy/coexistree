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

    /**
     * 从存储的相对路径解析完整的系统树路径
     * 支持跨平台路径解析
     *
     * @param systemTreeRoot 系统树根目录（如 /app/data/system-trees）
     * @param storedPath 存储的相对路径（如 order-service1/system_tree.json）
     * @return 完整的文件路径
     */
    public static Path resolveSystemTreePath(String systemTreeRoot, String storedPath) {
        if (storedPath == null || storedPath.isBlank()) {
            return null;
        }

        // 统一使用正斜杠处理
        String normalizedPath = storedPath.replace('\\', '/');

        // 如果已经是绝对路径（以根目录开头），直接返回
        if (normalizedPath.startsWith(systemTreeRoot.replace('\\', '/'))) {
            return Path.of(storedPath);
        }

        // 如果是旧格式的完整路径（包含 data/system-trees），提取相对部分
        if (normalizedPath.contains("system-trees/")) {
            int index = normalizedPath.indexOf("system-trees/");
            String relativePart = normalizedPath.substring(index + "system-trees/".length());
            return Path.of(systemTreeRoot, relativePart);
        }

        // 否则视为相对路径，直接拼接
        return Path.of(systemTreeRoot, normalizedPath);
    }

    /**
     * 获取用于存储的相对路径
     *
     * @param systemCode 系统代码
     * @return 相对路径（如 order-service1/system_tree.json）
     */
    public static String getRelativeSystemTreePath(String systemCode) {
        return systemCode + "/system_tree.json";
    }
}

