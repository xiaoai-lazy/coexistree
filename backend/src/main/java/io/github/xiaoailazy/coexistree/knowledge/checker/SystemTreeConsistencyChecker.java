package io.github.xiaoailazy.coexistree.knowledge.checker;

import io.github.xiaoailazy.coexistree.config.AppStorageProperties;
import io.github.xiaoailazy.coexistree.knowledge.entity.SystemKnowledgeTreeEntity;
import io.github.xiaoailazy.coexistree.knowledge.model.SystemKnowledgeTree;
import io.github.xiaoailazy.coexistree.knowledge.repository.SystemKnowledgeTreeRepository;
import io.github.xiaoailazy.coexistree.knowledge.storage.SystemTreeFileLoader;
import io.github.xiaoailazy.coexistree.indexer.tree.TreeNodeCounter;
import io.github.xiaoailazy.coexistree.shared.util.FilePathUtils;
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
    private final AppStorageProperties storageProperties;

    public SystemTreeConsistencyChecker(
            SystemKnowledgeTreeRepository repository,
            SystemTreeFileLoader fileLoader,
            TreeNodeCounter nodeCounter,
            AppStorageProperties storageProperties) {
        this.repository = repository;
        this.fileLoader = fileLoader;
        this.nodeCounter = nodeCounter;
        this.storageProperties = storageProperties;
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

        // 18.1.3 校验文件是否存在（使用相对路径解析）
        Path path = FilePathUtils.resolveSystemTreePath(storageProperties.systemTreeRoot(), treeFilePath);

        if (!Files.exists(path)) {
            log.warn("系统 {} 的知识树文件不存在, path={}, 标记为 EMPTY", systemId, path);
            entity.setTreeStatus("EMPTY");
            repository.save(entity);
            return;
        }

        // 如果是旧格式的绝对路径，迁移为相对路径
        String expectedRelativePath = FilePathUtils.getRelativeSystemTreePath(extractSystemCode(treeFilePath, systemId));
        if (!expectedRelativePath.equals(treeFilePath)) {
            log.info("系统 {} 的知识树路径已标准化为相对路径: {} -> {}", systemId, treeFilePath, expectedRelativePath);
            entity.setTreeFilePath(expectedRelativePath);
        }

        // 文件存在但状态为 EMPTY，自动恢复为 ACTIVE
        if ("EMPTY".equals(entity.getTreeStatus())) {
            log.info("系统 {} 的知识树文件已存在, 状态从 EMPTY 恢复为 ACTIVE", systemId);
            entity.setTreeStatus("ACTIVE");
            repository.save(entity);
        }

        // 18.1.4 校验 JSON 格式
        SystemKnowledgeTree tree;
        try {
            tree = fileLoader.load(path);
        } catch (Exception e) {
            log.warn("系统 {} 的知识树文件格式无效, path={}, 标记为 EMPTY", systemId, path, e);
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

    /**
     * 从存储路径中提取系统代码
     */
    private String extractSystemCode(String storedPath, Long systemId) {
        if (storedPath == null || storedPath.isBlank()) {
            return null;
        }

        // 处理相对路径（如 order-service1/system_tree.json）
        String normalizedPath = storedPath.replace('\\', '/');
        String[] parts = normalizedPath.split("/");

        // 查找 system_tree.json 的父目录
        for (int i = 0; i < parts.length; i++) {
            if ("system_tree.json".equals(parts[i]) && i > 0) {
                return parts[i - 1];
            }
        }

        // 如果无法从路径解析，从数据库查找系统代码（备用方案）
        return repository.findById(systemId)
                .map(e -> {
                    // 尝试再次从路径解析
                    String path = e.getTreeFilePath();
                    if (path != null) {
                        String[] p = path.replace('\\', '/').split("/");
                        for (int i = 0; i < p.length; i++) {
                            if ("system_tree.json".equals(p[i]) && i > 0) {
                                return p[i - 1];
                            }
                        }
                    }
                    return null;
                })
                .orElse(null);
    }
}
