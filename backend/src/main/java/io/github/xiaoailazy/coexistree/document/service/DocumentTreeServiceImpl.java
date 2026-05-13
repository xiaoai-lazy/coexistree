package io.github.xiaoailazy.coexistree.document.service;

import io.github.xiaoailazy.coexistree.shared.enums.ErrorCode;
import io.github.xiaoailazy.coexistree.shared.exception.BusinessException;
import io.github.xiaoailazy.coexistree.shared.util.JsonUtils;
import io.github.xiaoailazy.coexistree.document.entity.DocumentTreeEntity;
import io.github.xiaoailazy.coexistree.document.repository.DocumentTreeRepository;
import io.github.xiaoailazy.coexistree.indexer.model.DocumentTree;
import io.github.xiaoailazy.coexistree.indexer.model.TreeNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class DocumentTreeServiceImpl implements DocumentTreeService {

    private final DocumentTreeRepository documentTreeRepository;
    private final JsonUtils jsonUtils;

    public DocumentTreeServiceImpl(DocumentTreeRepository documentTreeRepository,
                                   JsonUtils jsonUtils) {
        this.documentTreeRepository = documentTreeRepository;
        this.jsonUtils = jsonUtils;
    }

    @Override
    public String getNodeText(Long documentId, String nodeId) {
        log.debug("获取节点文本, documentId={}, nodeId={}", documentId, nodeId);

        DocumentTreeEntity treeEntity = documentTreeRepository.findByDocumentId(documentId)
                .orElseThrow(() -> {
                    log.warn("文档树不存在, documentId={}", documentId);
                    throw new BusinessException(ErrorCode.TREE_FILE_NOT_FOUND,
                            "Document tree not found for documentId: " + documentId);
                });

        DocumentTree documentTree = jsonUtils.fromJson(treeEntity.getTreeJson(), DocumentTree.class);

        // 11.2.1.3 查找节点（递归遍历）
        TreeNode targetNode = findNodeById(documentTree.getStructure(), nodeId);

        // 11.2.1.5 异常处理（节点不存在）
        if (targetNode == null) {
            log.debug("节点不存在, documentId={}, nodeId={}, 返回空字符串", documentId, nodeId);
            return "";
        }

        // 11.2.1.4 返回节点的 text 字段
        String text = targetNode.getText();
        log.debug("成功获取节点文本, documentId={}, nodeId={}, textLength={}",
                documentId, nodeId, text != null ? text.length() : 0);

        return text != null ? text : "";
    }

    /**
     * 递归查找节点
     */
    private TreeNode findNodeById(List<TreeNode> nodes, String nodeId) {
        if (nodes == null || nodes.isEmpty()) {
            return null;
        }

        for (TreeNode node : nodes) {
            if (nodeId.equals(node.getNodeId())) {
                return node;
            }

            // 递归查找子节点
            TreeNode found = findNodeById(node.getNodes(), nodeId);
            if (found != null) {
                return found;
            }
        }

        return null;
    }
}
