package io.github.xiaoailazy.coexistree.indexer.storage;

import io.github.xiaoailazy.coexistree.shared.enums.ErrorCode;
import io.github.xiaoailazy.coexistree.shared.exception.BusinessException;
import io.github.xiaoailazy.coexistree.shared.util.JsonUtils;
import io.github.xiaoailazy.coexistree.indexer.model.DocumentTree;
import io.github.xiaoailazy.coexistree.indexer.tree.TreeNodeCounter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
@Component
public class TreeFileLoader {

    private final JsonUtils jsonUtils;
    private final TreeNodeCounter treeNodeCounter;

    public TreeFileLoader(JsonUtils jsonUtils, TreeNodeCounter treeNodeCounter) {
        this.jsonUtils = jsonUtils;
        this.treeNodeCounter = treeNodeCounter;
    }

    public DocumentTree load(Path path) {
        log.debug("加载树文件, path={}", path);

        try {
            String content = Files.readString(path, StandardCharsets.UTF_8);
            DocumentTree tree = jsonUtils.fromJson(content, DocumentTree.class);
            log.debug("树文件加载成功, docName={}, 节点数量={}",
                    tree.getDocName(), treeNodeCounter.count(tree.getStructure()));
            return tree;
        } catch (IOException e) {
            log.error("加载树文件失败, path={}", path, e);
            throw new BusinessException(ErrorCode.TREE_FILE_NOT_FOUND, "Tree file not found");
        }
    }
}
