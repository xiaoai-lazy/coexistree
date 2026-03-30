package io.github.xiaoailazy.coexistree.document.storage;

import io.github.xiaoailazy.coexistree.shared.enums.ErrorCode;
import io.github.xiaoailazy.coexistree.shared.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class MarkdownFileStorageService {

    public Path save(Path targetPath, MultipartFile file) {
        try {
            Files.createDirectories(targetPath.getParent());
            Files.write(targetPath, file.getBytes());
            return targetPath;
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INVALID_FILE_CONTENT, "Failed to save markdown file");
        }
    }

    public String read(Path targetPath) {
        try {
            return Files.readString(targetPath, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND, "Failed to read markdown file");
        }
    }
}

