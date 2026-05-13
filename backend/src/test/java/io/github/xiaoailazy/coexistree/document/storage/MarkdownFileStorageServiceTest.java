package io.github.xiaoailazy.coexistree.document.storage;

import io.github.xiaoailazy.coexistree.shared.enums.ErrorCode;
import io.github.xiaoailazy.coexistree.shared.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarkdownFileStorageServiceTest {

    private MarkdownFileStorageService markdownFileStorageService;

    @BeforeEach
    void setUp() {
        markdownFileStorageService = new MarkdownFileStorageService();
    }

    @Test
    void testSaveSuccess(@TempDir Path tempDir, @Mock MultipartFile file) throws IOException {
        // Given
        Path targetPath = tempDir.resolve("docs").resolve("test.md");
        byte[] content = "# 测试文档\n\n这是内容".getBytes(StandardCharsets.UTF_8);

        when(file.getBytes()).thenReturn(content);

        // When
        Path result = markdownFileStorageService.save(targetPath, file);

        // Then
        assertThat(result).isEqualTo(targetPath);
        assertThat(Files.exists(targetPath)).isTrue();
        assertThat(Files.readString(targetPath, StandardCharsets.UTF_8))
                .isEqualTo("# 测试文档\n\n这是内容");
    }

    @Test
    void testSaveCreatesParentDirectories(@TempDir Path tempDir, @Mock MultipartFile file) throws IOException {
        // Given
        Path deepPath = tempDir.resolve("level1").resolve("level2").resolve("level3").resolve("doc.md");
        byte[] content = "内容".getBytes(StandardCharsets.UTF_8);

        when(file.getBytes()).thenReturn(content);
        assertThat(Files.exists(deepPath.getParent())).isFalse();

        // When
        markdownFileStorageService.save(deepPath, file);

        // Then
        assertThat(Files.exists(deepPath.getParent().getParent().getParent())).isTrue();
        assertThat(Files.exists(deepPath)).isTrue();
    }

    @Test
    void testSaveIOException(@TempDir Path tempDir, @Mock MultipartFile file) throws IOException {
        // Given
        Path targetPath = tempDir.resolve("test.md");

        when(file.getBytes()).thenThrow(new IOException("磁盘已满"));

        // Then
        assertThatThrownBy(() -> markdownFileStorageService.save(targetPath, file))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(ErrorCode.INVALID_FILE_CONTENT);
                })
                .hasMessageContaining("Failed to save markdown file");
    }

    @Test
    void testReadSuccess(@TempDir Path tempDir) throws IOException {
        // Given
        Path filePath = tempDir.resolve("readme.md");
        String content = "# README\n\n项目说明文档";
        Files.writeString(filePath, content, StandardCharsets.UTF_8);

        // When
        String result = markdownFileStorageService.read(filePath);

        // Then
        assertThat(result).isEqualTo(content);
    }

    @Test
    void testReadFileNotFound(@TempDir Path tempDir) {
        // Given
        Path nonExistentFile = tempDir.resolve("not-exist.md");

        // Then
        assertThatThrownBy(() -> markdownFileStorageService.read(nonExistentFile))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(ErrorCode.DOCUMENT_NOT_FOUND);
                })
                .hasMessageContaining("Failed to read markdown file");
    }

    @Test
    void testReadWithChineseContent(@TempDir Path tempDir) throws IOException {
        // Given
        Path filePath = tempDir.resolve("chinese.md");
        String content = "# 中文标题\n\n这是一段中文内容，包含特殊字符：【】、（）";
        Files.writeString(filePath, content, StandardCharsets.UTF_8);

        // When
        String result = markdownFileStorageService.read(filePath);

        // Then
        assertThat(result).isEqualTo(content);
    }

    @Test
    void testReadWithLargeFile(@TempDir Path tempDir) throws IOException {
        // Given
        Path filePath = tempDir.resolve("large.md");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            sb.append("这是第").append(i).append("行内容\n");
        }
        String content = sb.toString();
        Files.writeString(filePath, content, StandardCharsets.UTF_8);

        // When
        String result = markdownFileStorageService.read(filePath);

        // Then
        assertThat(result).isEqualTo(content);
        assertThat(result.lines().count()).isEqualTo(1000);
    }

    @Test
    void testSaveAndReadRoundTrip(@TempDir Path tempDir, @Mock MultipartFile file) throws IOException {
        // Given
        Path targetPath = tempDir.resolve("roundtrip.md");
        String originalContent = "# 原始内容\n\n- 列表项1\n- 列表项2\n\n> 引用文本";
        byte[] contentBytes = originalContent.getBytes(StandardCharsets.UTF_8);

        when(file.getBytes()).thenReturn(contentBytes);

        // When
        markdownFileStorageService.save(targetPath, file);
        String readContent = markdownFileStorageService.read(targetPath);

        // Then
        assertThat(readContent).isEqualTo(originalContent);
    }

    @Test
    void testSaveEmptyFile(@TempDir Path tempDir, @Mock MultipartFile file) throws IOException {
        // Given
        Path targetPath = tempDir.resolve("empty.md");
        when(file.getBytes()).thenReturn(new byte[0]);

        // When
        markdownFileStorageService.save(targetPath, file);

        // Then
        assertThat(Files.exists(targetPath)).isTrue();
        assertThat(Files.size(targetPath)).isEqualTo(0);
    }

    @Test
    void testReadEmptyFile(@TempDir Path tempDir) throws IOException {
        // Given
        Path filePath = tempDir.resolve("empty.md");
        Files.createFile(filePath);

        // When
        String result = markdownFileStorageService.read(filePath);

        // Then
        assertThat(result).isEmpty();
    }
}
