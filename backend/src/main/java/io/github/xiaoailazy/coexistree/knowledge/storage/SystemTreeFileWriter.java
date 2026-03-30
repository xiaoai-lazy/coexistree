package io.github.xiaoailazy.coexistree.knowledge.storage;

import io.github.xiaoailazy.coexistree.shared.enums.ErrorCode;
import io.github.xiaoailazy.coexistree.shared.exception.BusinessException;
import io.github.xiaoailazy.coexistree.shared.util.JsonUtils;
import io.github.xiaoailazy.coexistree.knowledge.model.SystemKnowledgeTree;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Slf4j
@Component
public class SystemTreeFileWriter {

    private final JsonUtils jsonUtils;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public SystemTreeFileWriter(JsonUtils jsonUtils) {
        this.jsonUtils = jsonUtils;
    }

    /**
     * Write SystemKnowledgeTree to file using temporary file + atomic rename strategy.
     * Uses write lock to ensure thread safety.
     *
     * @param path target file path (e.g., system_tree.json)
     * @param tree SystemKnowledgeTree to write
     * @throws BusinessException if write fails
     */
    public void write(Path path, SystemKnowledgeTree tree) {
        log.debug("写入系统树文件, path={}, systemCode={}, version={}", 
                  path, tree.getSystemCode(), tree.getTreeVersion());

        lock.writeLock().lock();
        try {
            // Create parent directories if they don't exist
            Files.createDirectories(path.getParent());

            // Serialize to JSON
            String json = jsonUtils.toPrettyJson(tree);

            // Write to temporary file
            Path tempPath = path.resolveSibling(path.getFileName() + ".tmp");
            Files.writeString(tempPath, json, StandardCharsets.UTF_8);

            // Atomic rename
            Files.move(tempPath, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

            log.info("系统树文件写入成功, path={}, version={}, size={}bytes", 
                     path, tree.getTreeVersion(), json.length());
        } catch (IOException e) {
            log.error("写入系统树文件失败, path={}", path, e);
            throw new BusinessException(ErrorCode.SYSTEM_TREE_WRITE_FAILED, "Failed to write system tree file");
        } finally {
            lock.writeLock().unlock();
        }
    }
}
