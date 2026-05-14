package io.github.xiaoailazy.coexistree.chat.service;

import io.github.xiaoailazy.coexistree.chat.dto.SseEvent;
import io.github.xiaoailazy.coexistree.chat.service.impl.ChatSourceServiceImpl;
import io.github.xiaoailazy.coexistree.document.entity.DocumentEntity;
import io.github.xiaoailazy.coexistree.document.repository.DocumentRepository;
import io.github.xiaoailazy.coexistree.document.service.DocumentAccessService;
import io.github.xiaoailazy.coexistree.document.service.DocumentTreeService;
import io.github.xiaoailazy.coexistree.featuretree.model.EvidenceSource;
import io.github.xiaoailazy.coexistree.indexer.model.NodeSource;
import io.github.xiaoailazy.coexistree.indexer.model.TreeNode;
import io.github.xiaoailazy.coexistree.indexer.model.TreeSearchResult;
import io.github.xiaoailazy.coexistree.knowledge.model.SystemKnowledgeTree;
import io.github.xiaoailazy.coexistree.knowledge.service.SystemKnowledgeTreeService;
import io.github.xiaoailazy.coexistree.security.model.SecurityUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatSourceServiceTest {

    @Mock
    private SystemKnowledgeTreeService systemKnowledgeTreeService;
    @Mock
    private TreeSearchService treeSearchService;
    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private DocumentTreeService documentTreeService;
    @Mock
    private DocumentAccessService documentAccessService;
    @Mock
    private SecurityUserDetails userDetails;

    private ChatSourceService service;

    @BeforeEach
    void setUp() {
        service = new ChatSourceServiceImpl(
                systemKnowledgeTreeService,
                treeSearchService,
                documentRepository,
                documentTreeService,
                documentAccessService
        );
    }

    @Test
    void shouldReturnAuthorizedSourcesForMatchedNestedNodes() {
        TreeNode root = node("root", "交易", "交易总览", null, 1, source(1L, "root"));
        TreeNode child = node("refund", "退款规则", "允许退款的条件", "退款规则原文很长，需要截断为摘要", 2, source(2L, "refund-doc-node"));
        root.setNodes(List.of(child));
        SystemKnowledgeTree tree = tree(root);
        DocumentEntity doc = document(2L, "支付系统需求.md", 2);

        when(systemKnowledgeTreeService.getActiveTree(99L)).thenReturn(tree);
        when(treeSearchService.search(List.of(root), "退款", null)).thenReturn(new TreeSearchResult(null, null, List.of("refund")));
        when(documentRepository.findById(2L)).thenReturn(Optional.of(doc));
        when(documentAccessService.canReadDocument(doc, userDetails)).thenReturn(true);
        when(documentTreeService.getNodeText(2L, "refund-doc-node")).thenReturn("退款规则原文很长，需要截断为摘要");

        List<SseEvent.SourceDto> sources = service.retrieveSources(99L, "退款", userDetails);

        assertThat(sources).hasSize(1);
        SseEvent.SourceDto source = sources.get(0);
        assertThat(source.docId()).isEqualTo(2L);
        assertThat(source.docName()).isEqualTo("支付系统需求.md");
        assertThat(source.nodeId()).isEqualTo("refund-doc-node");
        assertThat(source.title()).isEqualTo("退款规则");
        assertThat(source.path()).isEqualTo("交易 > 退款规则");
        assertThat(source.snippet()).contains("退款规则原文");
        assertThat(source.lineNum()).isEqualTo(86);
        assertThat(source.level()).isEqualTo(2);
    }

    @Test
    void shouldFilterUnreadableSourcesWithoutLeakingTheirExistence() {
        TreeNode root = node("root", "交易", "交易总览", null, 1, null);
        TreeNode child = node("secret", "内部风控", "高密级内容", "不能泄露", 2, source(3L, "secret-node"));
        root.setNodes(List.of(child));
        SystemKnowledgeTree tree = tree(root);
        DocumentEntity doc = document(3L, "内部风控.md", 5);

        when(systemKnowledgeTreeService.getActiveTree(99L)).thenReturn(tree);
        when(treeSearchService.search(List.of(root), "风控", null)).thenReturn(new TreeSearchResult(null, null, List.of("secret")));
        when(documentRepository.findById(3L)).thenReturn(Optional.of(doc));
        when(documentAccessService.canReadDocument(doc, userDetails)).thenReturn(false);

        assertThat(service.retrieveSources(99L, "风控", userDetails)).isEmpty();
    }

    @Test
    void shouldDeduplicateSameDocumentNodePair() {
        TreeNode node = node("refund", "退款规则", "摘要", null, 2, source(2L, "refund-doc-node"));
        node.setSources(List.of(source(2L, "refund-doc-node"), source(2L, "refund-doc-node")));
        List<TreeNode> structure = List.of(node);
        SystemKnowledgeTree tree = new SystemKnowledgeTree();
        tree.setStructure(structure);
        DocumentEntity doc = document(2L, "支付系统需求.md", 2);

        when(systemKnowledgeTreeService.getActiveTree(99L)).thenReturn(tree);
        when(treeSearchService.search(anyList(), eq("退款"), isNull()))
                .thenReturn(new TreeSearchResult(null, null, List.of("refund")));
        when(documentRepository.findById(2L)).thenReturn(Optional.of(doc));
        when(documentAccessService.canReadDocument(doc, userDetails)).thenReturn(true);
        when(documentTreeService.getNodeText(2L, "refund-doc-node")).thenReturn("退款内容");

        assertThat(service.retrieveSources(99L, "退款", userDetails)).hasSize(1);
    }

    @Test
    void shouldUseEvidenceSourcesWhenPresent() {
        TreeNode feature = node("f1", "登录功能", "摘要", null, 2, null);
        EvidenceSource es = new EvidenceSource();
        es.setDocId(2L);
        es.setNodeId("anchor-1");
        feature.setEvidenceSources(List.of(es));
        List<TreeNode> structure = List.of(feature);
        SystemKnowledgeTree tree = new SystemKnowledgeTree();
        tree.setStructure(structure);
        DocumentEntity doc = document(2L, "需求.md", 2);

        when(systemKnowledgeTreeService.getActiveTree(99L)).thenReturn(tree);
        when(treeSearchService.search(anyList(), eq("登录"), isNull()))
                .thenReturn(new TreeSearchResult(null, null, List.of("f1")));
        when(documentRepository.findById(2L)).thenReturn(Optional.of(doc));
        when(documentAccessService.canReadDocument(doc, userDetails)).thenReturn(true);
        when(documentTreeService.getNodeText(2L, "anchor-1")).thenReturn("登录实现细节");

        List<SseEvent.SourceDto> sources = service.retrieveSources(99L, "登录", userDetails);

        assertThat(sources).hasSize(1);
        assertThat(sources.get(0).nodeId()).isEqualTo("anchor-1");
        assertThat(sources.get(0).snippet()).contains("登录实现");
    }

    @Test
    void shouldReturnEmptyListWhenSystemIdIsMissing() {
        assertThat(service.retrieveSources(null, "退款", userDetails)).isEmpty();
    }

    private SystemKnowledgeTree tree(TreeNode root) {
        SystemKnowledgeTree tree = new SystemKnowledgeTree();
        tree.setStructure(List.of(root));
        return tree;
    }

    private TreeNode node(String nodeId, String title, String summary, String text, Integer level, NodeSource source) {
        TreeNode node = new TreeNode();
        node.setNodeId(nodeId);
        node.setTitle(title);
        node.setSummary(summary);
        node.setText(text);
        node.setLineNum(86);
        node.setLevel(level);
        if (source != null) {
            node.setSources(List.of(source));
        }
        return node;
    }

    private NodeSource source(Long docId, String nodeId) {
        NodeSource source = new NodeSource();
        source.setDocId(docId);
        source.setNodeId(nodeId);
        source.setSecurityLevel(2);
        return source;
    }

    private DocumentEntity document(Long docId, String docName, Integer securityLevel) {
        DocumentEntity document = new DocumentEntity();
        document.setId(docId);
        document.setSystemId(99L);
        document.setDocName(docName);
        document.setSecurityLevel(securityLevel);
        return document;
    }
}
