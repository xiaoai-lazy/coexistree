package io.github.xiaoailazy.coexistree.knowledge.storage;

import io.github.xiaoailazy.coexistree.shared.enums.ErrorCode;
import io.github.xiaoailazy.coexistree.shared.exception.BusinessException;
import io.github.xiaoailazy.coexistree.shared.util.JsonUtils;
import io.github.xiaoailazy.coexistree.knowledge.model.SystemKnowledgeTree;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SystemTreeFileWriterTest {

    @TempDir
    Path tempDir;

    private JsonUtils jsonUtils;
    private SystemTreeFileWriter writer;

    @BeforeEach
    void setUp() {
        jsonUtils = mock(JsonUtils.class);
        writer = new SystemTreeFileWriter(jsonUtils);
    }

    @Test
    void shouldWriteTreeToFile() throws IOException {
        // Given
        SystemKnowledgeTree tree = createTestTree();
        Path targetPath = tempDir.resolve("system_tree.json");
        String expectedJson = "{\"systemCode\":\"test\"}";
        when(jsonUtils.toPrettyJson(any())).thenReturn(expectedJson);

        // When
        writer.write(targetPath, tree);

        // Then
        assertThat(targetPath).exists();
        String content = Files.readString(targetPath);
        assertThat(content).isEqualTo(expectedJson);
    }

    @Test
    void shouldCreateParentDirectories() throws IOException {
        // Given
        SystemKnowledgeTree tree = createTestTree();
        Path targetPath = tempDir.resolve("subdir/system_tree.json");
        when(jsonUtils.toPrettyJson(any())).thenReturn("{}");

        // When
        writer.write(targetPath, tree);

        // Then
        assertThat(targetPath.getParent()).exists();
        assertThat(targetPath).exists();
    }

    @Test
    void shouldReplaceExistingFile() throws IOException {
        // Given
        SystemKnowledgeTree tree = createTestTree();
        Path targetPath = tempDir.resolve("system_tree.json");
        Files.writeString(targetPath, "old content");
        String newJson = "{\"systemCode\":\"new\"}";
        when(jsonUtils.toPrettyJson(any())).thenReturn(newJson);

        // When
        writer.write(targetPath, tree);

        // Then
        String content = Files.readString(targetPath);
        assertThat(content).isEqualTo(newJson);
    }

    @Test
    void shouldNotLeaveTempFileOnSuccess() throws IOException {
        // Given
        SystemKnowledgeTree tree = createTestTree();
        Path targetPath = tempDir.resolve("system_tree.json");
        when(jsonUtils.toPrettyJson(any())).thenReturn("{}");

        // When
        writer.write(targetPath, tree);

        // Then
        Path tempPath = targetPath.resolveSibling("system_tree.json.tmp");
        assertThat(tempPath).doesNotExist();
    }

    @Test
    void shouldThrowBusinessExceptionOnIOError() throws IOException {
        // Given - 使用一个无法创建父目录的路径来模拟 IO 错误
        SystemKnowledgeTree tree = createTestTree();
        // 使用已经存在的文件作为父目录，导致无法创建子目录
        Path existingFile = tempDir.resolve("existing_file");
        Files.writeString(existingFile, "content");
        Path invalidPath = existingFile.resolve("subdir/system_tree.json");
        when(jsonUtils.toPrettyJson(any())).thenReturn("{}");

        // When/Then
        assertThatThrownBy(() -> writer.write(invalidPath, tree))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SYSTEM_TREE_WRITE_FAILED);
    }

    private SystemKnowledgeTree createTestTree() {
        SystemKnowledgeTree tree = new SystemKnowledgeTree();
        tree.setSystemId(1L);
        tree.setSystemCode("test");
        tree.setSystemName("Test System");
        tree.setTreeVersion(1);
        tree.setCreatedAt(LocalDateTime.now());
        tree.setLastUpdatedAt(LocalDateTime.now());
        return tree;
    }
}
