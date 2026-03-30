package io.github.xiaoailazy.coexistree.indexer.tree;

import io.github.xiaoailazy.coexistree.indexer.model.TreeNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class TreeSanitizer {

    public List<TreeNode> removeText(List<TreeNode> structure) {
        log.debug("移除树结构中的text字段, 节点数量={}", structure.size());
        List<TreeNode> result = new ArrayList<>();
        for (TreeNode node : structure) {
            TreeNode copy = new TreeNode();
            copy.setNodeId(node.getNodeId());
            copy.setTitle(node.getTitle());
            copy.setLineNum(node.getLineNum());
            copy.setLevel(node.getLevel());
            copy.setSummary(node.getSummary());
            copy.setPrefixSummary(node.getPrefixSummary());
            copy.setNodes(removeText(node.getNodes()));
            result.add(copy);
        }
        return result;
    }
}
