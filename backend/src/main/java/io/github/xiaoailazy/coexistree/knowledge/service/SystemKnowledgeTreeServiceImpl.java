package io.github.xiaoailazy.coexistree.knowledge.service;

import io.github.xiaoailazy.coexistree.knowledge.entity.SystemKnowledgeTreeEntity;
import io.github.xiaoailazy.coexistree.knowledge.model.SystemKnowledgeTree;
import io.github.xiaoailazy.coexistree.knowledge.repository.SystemKnowledgeTreeRepository;
import io.github.xiaoailazy.coexistree.shared.enums.ErrorCode;
import io.github.xiaoailazy.coexistree.shared.exception.BusinessException;
import io.github.xiaoailazy.coexistree.shared.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class SystemKnowledgeTreeServiceImpl implements SystemKnowledgeTreeService {

    private final SystemKnowledgeTreeRepository systemKnowledgeTreeRepository;
    private final JsonUtils jsonUtils;

    public SystemKnowledgeTreeServiceImpl(
            SystemKnowledgeTreeRepository systemKnowledgeTreeRepository,
            JsonUtils jsonUtils) {
        this.systemKnowledgeTreeRepository = systemKnowledgeTreeRepository;
        this.jsonUtils = jsonUtils;
    }

    @Override
    public SystemKnowledgeTree getActiveTree(Long systemId) {
        log.debug("获取活跃系统知识树, systemId={}", systemId);

        SystemKnowledgeTreeEntity entity = systemKnowledgeTreeRepository
                .findBySystemIdAndTreeStatus(systemId, "ACTIVE")
                .orElseThrow(
                        () -> {
                            log.error("系统知识树不存在, systemId={}", systemId);
                            return new BusinessException(
                                    ErrorCode.SYSTEM_TREE_NOT_FOUND,
                                    "System knowledge tree not found for systemId: " + systemId);
                        });

        if (!"ACTIVE".equals(entity.getTreeStatus())) {
            log.error("系统知识树状态不为 ACTIVE, systemId={}, status={}", systemId, entity.getTreeStatus());
            throw new BusinessException(
                    ErrorCode.SYSTEM_TREE_NOT_READY,
                    "System knowledge tree is not ready, current status: " + entity.getTreeStatus());
        }

        SystemKnowledgeTree tree = jsonUtils.fromJson(entity.getTreeJson(), SystemKnowledgeTree.class);

        log.info(
                "成功获取活跃系统知识树, systemId={}, treeVersion={}, nodeCount={}",
                systemId, entity.getTreeVersion(), entity.getNodeCount());

        return tree;
    }
}
