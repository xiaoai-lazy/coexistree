package io.github.xiaoailazy.coexistree.chat.service.impl;

import io.github.xiaoailazy.coexistree.chat.dto.SseEvent;
import io.github.xiaoailazy.coexistree.chat.service.ChatSourceService;
import io.github.xiaoailazy.coexistree.chat.service.TreeSearchService;
import io.github.xiaoailazy.coexistree.document.entity.DocumentEntity;
import io.github.xiaoailazy.coexistree.document.repository.DocumentRepository;
import io.github.xiaoailazy.coexistree.document.service.DocumentAccessService;
import io.github.xiaoailazy.coexistree.document.service.DocumentTreeService;
import io.github.xiaoailazy.coexistree.indexer.model.NodeSource;
import io.github.xiaoailazy.coexistree.indexer.model.TreeNode;
import io.github.xiaoailazy.coexistree.indexer.model.TreeSearchResult;
import io.github.xiaoailazy.coexistree.knowledge.model.SystemKnowledgeTree;
import io.github.xiaoailazy.coexistree.knowledge.service.SystemKnowledgeTreeService;
import io.github.xiaoailazy.coexistree.security.model.SecurityUserDetails;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
public class ChatSourceServiceImpl implements ChatSourceService {

    private static final int MAX_SOURCES = 5;
    private static final int MAX_SNIPPET_LENGTH = 180;

    private final SystemKnowledgeTreeService systemKnowledgeTreeService;
    private final TreeSearchService treeSearchService;
    private final DocumentRepository documentRepository;
    private final DocumentTreeService documentTreeService;
    private final DocumentAccessService documentAccessService;

    public ChatSourceServiceImpl(
            SystemKnowledgeTreeService systemKnowledgeTreeService,
            TreeSearchService treeSearchService,
            DocumentRepository documentRepository,
            DocumentTreeService documentTreeService,
            DocumentAccessService documentAccessService
    ) {
        this.systemKnowledgeTreeService = systemKnowledgeTreeService;
        this.treeSearchService = treeSearchService;
        this.documentRepository = documentRepository;
        this.documentTreeService = documentTreeService;
        this.documentAccessService = documentAccessService;
    }

    @Override
    public List<SseEvent.SourceDto> retrieveSources(Long systemId, String query, SecurityUserDetails userDetails) {
        if (systemId == null || query == null || query.isBlank()) {
            return List.of();
        }

        try {
            SystemKnowledgeTree tree = systemKnowledgeTreeService.getActiveTree(systemId);
            List<TreeNode> structure = tree.getStructure();
            if (structure == null || structure.isEmpty()) {
                return List.of();
            }
            TreeSearchResult searchResult = treeSearchService.search(structure, query, null);
            if (searchResult.getNodeList() == null || searchResult.getNodeList().isEmpty()) {
                return List.of();
            }

            Map<String, TreeNode> nodeById = new HashMap<>();
            indexNodes(structure, nodeById);

            List<SseEvent.SourceDto> sources = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (String nodeId : searchResult.getNodeList()) {
                TreeNode node = nodeById.get(nodeId);
                if (node == null || node.getSources() == null) {
                    continue;
                }

                for (NodeSource source : node.getSources()) {
                    if (sources.size() >= MAX_SOURCES) {
                        return sources;
                    }
                    toSourceDto(source, node, structure, userDetails, seen).ifPresent(sources::add);
                }
            }
            return sources;
        } catch (Exception e) {
            log.warn("检索聊天来源失败, systemId={}", systemId, e);
            return List.of();
        }
    }

    private Optional<SseEvent.SourceDto> toSourceDto(
            NodeSource source,
            TreeNode matchedNode,
            List<TreeNode> structure,
            SecurityUserDetails userDetails,
            Set<String> seen
    ) {
        if (source.getDocId() == null || source.getNodeId() == null) {
            return Optional.empty();
        }
        String key = source.getDocId() + ":" + source.getNodeId();
        if (!seen.add(key)) {
            return Optional.empty();
        }

        return documentRepository.findById(source.getDocId())
                .filter(document -> documentAccessService.canReadDocument(document, userDetails))
                .map(document -> new SseEvent.SourceDto(
                        document.getId(),
                        document.getDocName(),
                        source.getNodeId(),
                        matchedNode.getTitle(),
                        buildPath(structure, matchedNode.getNodeId()),
                        snippet(document.getId(), source.getNodeId(), matchedNode),
                        matchedNode.getLineNum(),
                        matchedNode.getLevel()
                ));
    }

    private void indexNodes(List<TreeNode> nodes, Map<String, TreeNode> nodeById) {
        if (nodes == null) {
            return;
        }
        for (TreeNode node : nodes) {
            if (node.getNodeId() != null) {
                nodeById.put(node.getNodeId(), node);
            }
            indexNodes(node.getNodes(), nodeById);
        }
    }

    private String buildPath(List<TreeNode> nodes, String targetNodeId) {
        List<String> titles = new ArrayList<>();
        if (findPath(nodes, targetNodeId, titles)) {
            return String.join(" > ", titles);
        }
        return targetNodeId;
    }

    private boolean findPath(List<TreeNode> nodes, String targetNodeId, List<String> titles) {
        if (nodes == null) {
            return false;
        }
        for (TreeNode node : nodes) {
            titles.add(node.getTitle() != null ? node.getTitle() : node.getNodeId());
            if (targetNodeId.equals(node.getNodeId()) || findPath(node.getNodes(), targetNodeId, titles)) {
                return true;
            }
            titles.remove(titles.size() - 1);
        }
        return false;
    }

    private String snippet(Long docId, String nodeId, TreeNode matchedNode) {
        String text = documentTreeService.getNodeText(docId, nodeId);
        if (text == null || text.isBlank()) {
            text = matchedNode.getSummary();
        }
        if (text == null || text.isBlank()) {
            text = matchedNode.getText();
        }
        if (text == null) {
            return null;
        }
        String normalized = text.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= MAX_SNIPPET_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, MAX_SNIPPET_LENGTH) + "...";
    }
}
