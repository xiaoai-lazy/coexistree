package io.github.xiaoailazy.coexistree.shared.util;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class FilePathUtilsTest {

    @Test
    void markdownPath_shouldReturnCorrectPath() {
        Path result = FilePathUtils.markdownPath("./data/docs", "test-system", 123L);
        assertThat(result).isEqualTo(Path.of("./data/docs", "test-system", "123.md"));
    }

    @Test
    void treePath_shouldReturnCorrectPath() {
        Path result = FilePathUtils.treePath("./data/trees", "test-system", 456L);
        assertThat(result).isEqualTo(Path.of("./data/trees", "test-system", "456_tree.json"));
    }

    @Test
    void systemTreePath_shouldReturnCorrectPath() {
        Path result = FilePathUtils.systemTreePath("./data/system-trees", "my-system");
        assertThat(result).isEqualTo(Path.of("./data/system-trees", "my-system", "system_tree.json"));
    }

    @Test
    void systemTreePath_shouldHandleVariousSystemCodes() {
        Path result1 = FilePathUtils.systemTreePath("./data/system-trees", "system-a");
        assertThat(result1).isEqualTo(Path.of("./data/system-trees", "system-a", "system_tree.json"));

        Path result2 = FilePathUtils.systemTreePath("./data/system-trees", "system-b");
        assertThat(result2).isEqualTo(Path.of("./data/system-trees", "system-b", "system_tree.json"));
    }
}
