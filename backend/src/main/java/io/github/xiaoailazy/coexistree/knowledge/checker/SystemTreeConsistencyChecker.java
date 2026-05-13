package io.github.xiaoailazy.coexistree.knowledge.checker;

import io.github.xiaoailazy.coexistree.knowledge.entity.SystemKnowledgeTreeEntity;
import io.github.xiaoailazy.coexistree.knowledge.model.SystemKnowledgeTree;
import io.github.xiaoailazy.coexistree.knowledge.repository.SystemKnowledgeTreeRepository;
import io.github.xiaoailazy.coexistree.indexer.tree.TreeNodeCounter;
import io.github.xiaoailazy.coexistree.shared.util.JsonUtils;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 系统知识树一致性检查器
 * 在应用启动时检查所有系统知识树的一致性
 */
@Slf4j
@Component
public class SystemTreeConsistencyChecker {

    private final SystemKnowledgeTreeRepository repository;
    private final TreeNodeCounter nodeCounter;
    private final JsonUtils jsonUtils;

    public SystemTreeConsistencyChecker(
            SystemKnowledgeTreeRepository repository,
            TreeNodeCounter nodeCounter,
            JsonUtils jsonUtils) {
        this.repository = repository;
        this.nodeCounter = nodeCounter;
        this.jsonUtils = jsonUtils;
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
        log.debug("检查系统 {} 的知识树", systemId);

        SystemKnowledgeTree tree;
        try {
            tree = jsonUtils.fromJson(entity.getTreeJson(), SystemKnowledgeTree.class);
        } catch (Exception e) {
            log.warn("系统 {} 的知识树 JSON 无效, 标记为 EMPTY", systemId, e);
            entity.setTreeStatus("EMPTY");
            repository.save(entity);
            return;
        }

        if (!entity.getTreeVersion().equals(tree.getTreeVersion())) {
            log.warn("系统 {} 的 treeVersion 不一致, DB={}, JSON={}, 以数据库为准",
                    systemId, entity.getTreeVersion(), tree.getTreeVersion());
        }

        int actualNodeCount = nodeCounter.count(tree.getStructure());
        if (!entity.getNodeCount().equals(actualNodeCount)) {
            log.warn("系统 {} 的 nodeCount 不一致, DB={}, JSON={}, 更新数据库记录",
                    systemId, entity.getNodeCount(), actualNodeCount);
            entity.setNodeCount(actualNodeCount);
            repository.save(entity);
        }

        log.debug("系统 {} 的知识树一致性检查通过", systemId);
    }
}
