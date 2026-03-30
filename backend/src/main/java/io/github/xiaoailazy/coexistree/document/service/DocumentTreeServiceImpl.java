package io.github.xiaoailazy.coexistree.document.service;

import io.github.xiaoailazy.coexistree.shared.enums.ErrorCode;
import io.github.xiaoailazy.coexistree.shared.exception.BusinessException;
import io.github.xiaoailazy.coexistree.document.entity.DocumentTreeEntity;
import io.github.xiaoailazy.coexistree.document.repository.DocumentTreeRepository;
import io.github.xiaoailazy.coexistree.indexer.model.DocumentTree;
import io.github.xiaoailazy.coexistree.indexer.model.TreeNode;
import io.github.xiaoailazy.coexistree.indexer.storage.TreeFileLoader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;

@Slf4j
@Service
public class DocumentTreeServiceImpl implements DocumentTreeService {

    private final DocumentTreeRepository documentTreeRepository;
    private final TreeFileLoader treeFileLoader;

    public DocumentTreeServiceImpl(DocumentTreeRepository documentTreeRepository,
                                   TreeFileLoader treeFileLoader) {
        this.documentTreeRepository = documentTreeRepository;
        this.treeFileLoader = treeFileLoader;
    }

    @Override
    public String getNodeText(Long documentId, String nodeId) {
        log.debug("获取节点文本, documentId={}, nodeId={}", documentId, nodeId);

        // 11.2.1.1 查询 document_trees 获取 tree_file_path
        DocumentTreeEntity treeEntity = documentTreeRepository.findByDocumentId(documentId)
                .orElseThrow(() -> {
                    log.warn("文档树不存在, documentId={}", documentId);
                    throw new BusinessException(ErrorCode.TREE_FILE_NOT_FOUND,
                            "Document tree not found for documentId: " + documentId);
                });

        // 11.2.1.2 加载文档树 JSON 文件
        Path treePath = Path.of(treeEntity.getTreeFilePath());
        DocumentTree documentTree = treeFileLoader.load(treePath);

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
