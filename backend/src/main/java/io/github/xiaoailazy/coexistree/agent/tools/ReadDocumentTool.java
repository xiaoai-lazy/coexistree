package io.github.xiaoailazy.coexistree.agent.tools;

import io.github.xiaoailazy.coexistree.document.entity.DocumentEntity;
import io.github.xiaoailazy.coexistree.document.repository.DocumentRepository;
import io.github.xiaoailazy.coexistree.document.storage.MarkdownFileStorageService;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;

/**
 * 读取指定文档的内容。
 */
@Slf4j
public class ReadDocumentTool {

    private final DocumentRepository documentRepository;
    private final MarkdownFileStorageService storageService;

    public ReadDocumentTool(
            DocumentRepository documentRepository,
            MarkdownFileStorageService storageService
    ) {
        this.documentRepository = documentRepository;
        this.storageService = storageService;
    }

    public String execute(Long docId) {
        try {
            DocumentEntity doc = documentRepository.findById(docId).orElse(null);
            if (doc == null) {
                return "文档 ID=" + docId + " 不存在。";
            }

            Path filePath = Path.of(doc.getFilePath());
            String content = storageService.read(filePath);
            if (content == null || content.isBlank()) {
                return "文档 " + doc.getDocName() + " 内容为空。";
            }
            return content;

        } catch (Exception e) {
            log.error("read_document 执行失败, docId={}", docId, e);
            return "读取文档失败: " + e.getMessage();
        }
    }
}
