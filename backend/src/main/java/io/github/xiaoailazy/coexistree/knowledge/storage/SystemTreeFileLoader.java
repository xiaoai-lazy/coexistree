package io.github.xiaoailazy.coexistree.knowledge.storage;

import io.github.xiaoailazy.coexistree.shared.enums.ErrorCode;
import io.github.xiaoailazy.coexistree.shared.exception.BusinessException;
import io.github.xiaoailazy.coexistree.shared.util.JsonUtils;
import io.github.xiaoailazy.coexistree.knowledge.model.SystemKnowledgeTree;
import io.github.xiaoailazy.coexistree.indexer.tree.TreeNodeCounter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
@Component
public class SystemTreeFileLoader {

    private final JsonUtils jsonUtils;
    private final TreeNodeCounter treeNodeCounter;

    public SystemTreeFileLoader(JsonUtils jsonUtils, TreeNodeCounter treeNodeCounter) {
        this.jsonUtils = jsonUtils;
        this.treeNodeCounter = treeNodeCounter;
    }

    public SystemKnowledgeTree load(Path path) {
        log.debug("加载系统知识树文件, path={}", path);

        try {
            String content = Files.readString(path, StandardCharsets.UTF_8);
            SystemKnowledgeTree tree = jsonUtils.fromJson(content, SystemKnowledgeTree.class);
            log.debug("系统知识树文件加载成功, systemName={}, 节点数量={}",
                    tree.getSystemName(), treeNodeCounter.count(tree.getStructure()));
            return tree;
        } catch (IOException e) {
            log.error("加载系统知识树文件失败, path={}", path, e);
            throw new BusinessException(ErrorCode.TREE_FILE_NOT_FOUND, "System tree file not found");
        }
    }
}
