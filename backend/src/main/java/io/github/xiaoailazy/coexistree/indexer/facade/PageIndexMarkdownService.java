package io.github.xiaoailazy.coexistree.indexer.facade;

import io.github.xiaoailazy.coexistree.indexer.model.DocumentTree;
import io.github.xiaoailazy.coexistree.indexer.model.FlatMarkdownNode;
import io.github.xiaoailazy.coexistree.indexer.model.PageIndexBuildOptions;
import io.github.xiaoailazy.coexistree.indexer.model.RawHeaderNode;
import io.github.xiaoailazy.coexistree.indexer.model.TreeNode;
import io.github.xiaoailazy.coexistree.indexer.parser.MarkdownNodeExtractor;
import io.github.xiaoailazy.coexistree.indexer.parser.MarkdownNodeTextAssembler;
import io.github.xiaoailazy.coexistree.indexer.summary.DocumentSummaryService;
import io.github.xiaoailazy.coexistree.indexer.summary.NodeSummaryService;
import io.github.xiaoailazy.coexistree.indexer.tree.PageIndexTreeBuilder;
import io.github.xiaoailazy.coexistree.indexer.tree.TreeNodeCounter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
public class PageIndexMarkdownService {

    private final MarkdownNodeExtractor markdownNodeExtractor;
    private final MarkdownNodeTextAssembler markdownNodeTextAssembler;
    private final PageIndexTreeBuilder pageIndexTreeBuilder;
    private final NodeSummaryService nodeSummaryService;
    private final DocumentSummaryService documentSummaryService;
    private final TreeNodeCounter treeNodeCounter;

    public PageIndexMarkdownService(
            MarkdownNodeExtractor markdownNodeExtractor,
            MarkdownNodeTextAssembler markdownNodeTextAssembler,
            PageIndexTreeBuilder pageIndexTreeBuilder,
            NodeSummaryService nodeSummaryService,
            DocumentSummaryService documentSummaryService,
            TreeNodeCounter treeNodeCounter
    ) {
        this.markdownNodeExtractor = markdownNodeExtractor;
        this.markdownNodeTextAssembler = markdownNodeTextAssembler;
        this.pageIndexTreeBuilder = pageIndexTreeBuilder;
        this.nodeSummaryService = nodeSummaryService;
        this.documentSummaryService = documentSummaryService;
        this.treeNodeCounter = treeNodeCounter;
    }

    public DocumentTree buildTree(Path markdownPath, PageIndexBuildOptions options) {
        log.info("开始构建文档树, path={}", markdownPath);

        String markdown = read(markdownPath);
        log.debug("读取Markdown文件完成, 内容长度={}", markdown.length());

        List<RawHeaderNode> headers = markdownNodeExtractor.extract(markdown);
        log.debug("提取标题节点完成, 标题数量={}", headers.size());

        List<String> lines = Arrays.asList(markdown.split("\\R", -1));
        List<FlatMarkdownNode> flatNodes = markdownNodeTextAssembler.assemble(headers, lines);
        log.debug("组装节点内容完成, 节点数量={}", flatNodes.size());

        if (options.thinning()) {
            flatNodes = thinNodes(updateNodeTokenCounts(flatNodes), options.minTokenThreshold());
            log.debug("节点精简完成, 精简后节点数量={}", flatNodes.size());
        }

        DocumentTree tree = new DocumentTree();
        String fileName = markdownPath.getFileName().toString();
        String docName = fileName.contains(".") ? fileName.substring(0, fileName.lastIndexOf('.')) : fileName;
        tree.setDocName(docName);
        tree.setStructure(pageIndexTreeBuilder.build(flatNodes));
        log.debug("构建树结构完成, 根节点数量={}", tree.getStructure().size());

        if (options.addNodeSummary()) {
            applyNodeSummaries(tree.getStructure(), options.summaryTokenThreshold(), options.model());
            log.debug("节点摘要生成完成");
            if (options.addDocDescription()) {
                tree.setDocDescription(documentSummaryService.summarize(
                        createCleanStructureForDescription(tree.getStructure()),
                        docName,
                        options.model()
                ));
                log.debug("文档摘要生成完成");
            }
        } else {
            clearSummaries(tree.getStructure());
        }

        normalizeOutput(tree.getStructure(), options);
        log.info("文档树构建完成, docName={}, 节点总数={}", tree.getDocName(), treeNodeCounter.count(tree.getStructure()));
        return tree;
    }

    private List<FlatMarkdownNode> updateNodeTokenCounts(List<FlatMarkdownNode> nodeList) {
        List<FlatMarkdownNode> result = copyFlatNodes(nodeList);
        for (int i = result.size() - 1; i >= 0; i--) {
            FlatMarkdownNode current = result.get(i);
            StringBuilder combined = new StringBuilder(nullToEmpty(current.getText()));
            for (int childIndex : findAllChildren(i, current.getLevel(), result)) {
                String childText = result.get(childIndex).getText();
                if (!nullToEmpty(childText).isBlank()) {
                    if (!combined.isEmpty()) {
                        combined.append('\n');
                    }
                    combined.append(childText);
                }
            }
            current.setTextTokenCount(countTokens(combined.toString()));
        }
        return result;
    }

    private List<FlatMarkdownNode> thinNodes(List<FlatMarkdownNode> nodeList, int minTokenThreshold) {
        List<FlatMarkdownNode> result = copyFlatNodes(nodeList);
        boolean[] removed = new boolean[result.size()];

        for (int i = result.size() - 1; i >= 0; i--) {
            if (removed[i]) {
                continue;
            }
            FlatMarkdownNode current = result.get(i);
            int totalTokens = current.getTextTokenCount() == null ? 0 : current.getTextTokenCount();
            if (totalTokens >= minTokenThreshold) {
                continue;
            }

            List<Integer> children = findAllChildren(i, current.getLevel(), result);
            List<String> childrenTexts = new ArrayList<>();
            for (int childIndex : children) {
                if (removed[childIndex]) {
                    continue;
                }
                String childText = nullToEmpty(result.get(childIndex).getText()).trim();
                if (!childText.isEmpty()) {
                    childrenTexts.add(childText);
                }
                removed[childIndex] = true;
            }

            if (!childrenTexts.isEmpty()) {
                String mergedText = mergeTexts(current.getText(), childrenTexts);
                current.setText(mergedText);
                current.setTextTokenCount(countTokens(mergedText));
            }
        }

        List<FlatMarkdownNode> thinned = new ArrayList<>();
        for (int i = 0; i < result.size(); i++) {
            if (!removed[i]) {
                thinned.add(result.get(i));
            }
        }
        return thinned;
    }

    private void applyNodeSummaries(List<TreeNode> nodes, int summaryTokenThreshold, String model) {
        for (TreeNode node : nodes) {
            String summary = nodeSummaryService.summarizeNodeText(node.getText(), summaryTokenThreshold, model);
            if (node.getNodes().isEmpty()) {
                node.setSummary(summary);
                node.setPrefixSummary(null);
            } else {
                node.setPrefixSummary(summary);
                node.setSummary(null);
                applyNodeSummaries(node.getNodes(), summaryTokenThreshold, model);
            }
        }
    }

    private void clearSummaries(List<TreeNode> nodes) {
        for (TreeNode node : nodes) {
            node.setSummary(null);
            node.setPrefixSummary(null);
            clearSummaries(node.getNodes());
        }
    }

    private List<TreeNode> createCleanStructureForDescription(List<TreeNode> nodes) {
        List<TreeNode> cleanNodes = new ArrayList<>();
        for (TreeNode node : nodes) {
            TreeNode cleanNode = new TreeNode();
            cleanNode.setTitle(node.getTitle());
            cleanNode.setNodeId(node.getNodeId());
            cleanNode.setSummary(node.getSummary());
            cleanNode.setPrefixSummary(node.getPrefixSummary());
            cleanNode.setNodes(createCleanStructureForDescription(node.getNodes()));
            cleanNodes.add(cleanNode);
        }
        return cleanNodes;
    }

    private void normalizeOutput(List<TreeNode> nodes, PageIndexBuildOptions options) {
        for (TreeNode node : nodes) {
            node.setLevel(null);
            if (!options.addNodeText()) {
                node.setText(null);
            }
            if (!options.addNodeId()) {
                node.setNodeId(null);
            }
            normalizeOutput(node.getNodes(), options);
        }
    }

    private List<Integer> findAllChildren(int parentIndex, Integer parentLevel, List<FlatMarkdownNode> nodeList) {
        List<Integer> children = new ArrayList<>();
        for (int i = parentIndex + 1; i < nodeList.size(); i++) {
            Integer currentLevel = nodeList.get(i).getLevel();
            if (currentLevel <= parentLevel) {
                break;
            }
            children.add(i);
        }
        return children;
    }

    private List<FlatMarkdownNode> copyFlatNodes(List<FlatMarkdownNode> nodeList) {
        List<FlatMarkdownNode> result = new ArrayList<>();
        for (FlatMarkdownNode node : nodeList) {
            FlatMarkdownNode copy = new FlatMarkdownNode();
            copy.setTitle(node.getTitle());
            copy.setLineNum(node.getLineNum());
            copy.setLevel(node.getLevel());
            copy.setText(node.getText());
            copy.setTextTokenCount(node.getTextTokenCount());
            result.add(copy);
        }
        return result;
    }

    private String mergeTexts(String parentText, List<String> childTexts) {
        StringBuilder merged = new StringBuilder(nullToEmpty(parentText).trim());
        for (String childText : childTexts) {
            if (!merged.isEmpty() && !merged.toString().endsWith("\n")) {
                merged.append("\n\n");
            }
            merged.append(childText);
        }
        return merged.toString();
    }

    private int countTokens(String text) {
        String normalized = nullToEmpty(text).trim();
        if (normalized.isEmpty()) {
            return 0;
        }
        return normalized.split("\\s+").length;
    }

    private String nullToEmpty(String text) {
        return text == null ? "" : text;
    }

    private String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("读取Markdown文件失败, path={}", path, e);
            throw new IllegalStateException("Failed to read markdown", e);
        }
    }
}
