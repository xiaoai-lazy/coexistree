package io.github.xiaoailazy.coexistree.knowledge.checker;

import io.github.xiaoailazy.coexistree.knowledge.entity.SystemKnowledgeTreeEntity;
import io.github.xiaoailazy.coexistree.knowledge.model.SystemKnowledgeTree;
import io.github.xiaoailazy.coexistree.knowledge.repository.SystemKnowledgeTreeRepository;
import io.github.xiaoailazy.coexistree.knowledge.storage.SystemTreeFileLoader;
import io.github.xiaoailazy.coexistree.indexer.tree.TreeNodeCounter;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 系统知识树一致性检查器
 * 在应用启动时检查所有系统知识树的一致性
 */
@Slf4j
@Component
public class SystemTreeConsistencyChecker {

    private final SystemKnowledgeTreeRepository repository;
    private final SystemTreeFileLoader fileLoader;
    private final TreeNodeCounter nodeCounter;

    public SystemTreeConsistencyChecker(
            SystemKnowledgeTreeRepository repository,
            SystemTreeFileLoader fileLoader,
            TreeNodeCounter nodeCounter) {
        this.repository = repository;
        this.fileLoader = fileLoader;
        this.nodeCounter = nodeCounter;
    }

    /**
     * 应用启动时检查所有系统知识树
     */
    @PostConstruct
    public void checkAllTrees() {
        log.info("开始检查系统知识树一致性");
        
        List<SystemKnowledgeTreeEntity> allTrees = repository.findAll();
        log.info("找到 {} 个系统知识树记录", allTrees.size());
        
        for (SystemKnowledgeTreeEntity entity : allTrees) {
            checkTree(entity);
        }
        
        log.info("系统知识树一致性检查完成");
    }

    /**
     * 检查单个系统知识树的一致性
     */
    private void checkTree(SystemKnowledgeTreeEntity entity) {
        Long systemId = entity.getSystemId();
        String treeFilePath = entity.getTreeFilePath();
        
        log.debug("检查系统 {} 的知识树, path={}", systemId, treeFilePath);
        
        // 18.1.3 校验文件是否存在
        Path path = Path.of(treeFilePath);
        if (!Files.exists(path)) {
            log.warn("系统 {} 的知识树文件不存在, path={}, 标记为 EMPTY", systemId, treeFilePath);
            entity.setTreeStatus("EMPTY");
            repository.save(entity);
            return;
        }
        
        // 18.1.4 校验 JSON 格式
        SystemKnowledgeTree tree;
        try {
            tree = fileLoader.load(path);
        } catch (Exception e) {
            log.warn("系统 {} 的知识树文件格式无效, path={}, 标记为 EMPTY", systemId, treeFilePath, e);
            entity.setTreeStatus("EMPTY");
            repository.save(entity);
            return;
        }
        
        // 18.1.5 校验 treeVersion 一致性
        if (!entity.getTreeVersion().equals(tree.getTreeVersion())) {
            log.warn("系统 {} 的 treeVersion 不一致, DB={}, File={}, 以数据库为准",
                    systemId, entity.getTreeVersion(), tree.getTreeVersion());
        }
        
        // 18.1.6 校验 nodeCount 一致性
        int actualNodeCount = nodeCounter.count(tree.getStructure());
        if (!entity.getNodeCount().equals(actualNodeCount)) {
            log.warn("系统 {} 的 nodeCount 不一致, DB={}, File={}, 更新数据库记录",
                    systemId, entity.getNodeCount(), actualNodeCount);
            entity.setNodeCount(actualNodeCount);
            repository.save(entity);
        }
        
        log.debug("系统 {} 的知识树一致性检查通过", systemId);
    }
}
