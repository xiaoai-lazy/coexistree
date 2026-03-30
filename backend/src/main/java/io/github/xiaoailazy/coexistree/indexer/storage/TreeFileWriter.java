package io.github.xiaoailazy.coexistree.indexer.storage;

import io.github.xiaoailazy.coexistree.shared.enums.ErrorCode;
import io.github.xiaoailazy.coexistree.shared.exception.BusinessException;
import io.github.xiaoailazy.coexistree.shared.util.JsonUtils;
import io.github.xiaoailazy.coexistree.indexer.model.DocumentTree;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
@Component
public class TreeFileWriter {

    private final JsonUtils jsonUtils;

    public TreeFileWriter(JsonUtils jsonUtils) {
        this.jsonUtils = jsonUtils;
    }

    public void write(Path path, DocumentTree tree) {
        log.debug("写入树文件, path={}, docName={}", path, tree.getDocName());

        try {
            Files.createDirectories(path.getParent());
            String json = jsonUtils.toPrettyJson(tree);
            Files.writeString(path, json, StandardCharsets.UTF_8);
            log.info("树文件写入成功, path={}, size={}bytes", path, json.length());
        } catch (IOException e) {
            log.error("写入树文件失败, path={}", path, e);
            throw new BusinessException(ErrorCode.TREE_BUILD_FAILED, "Failed to write tree file");
        }
    }
}
