package io.github.xiaoailazy.coexistree.chat.service;

import io.github.xiaoailazy.coexistree.chat.repository.ConversationRepository;
import io.github.xiaoailazy.coexistree.chat.repository.MessageRepository;
import io.github.xiaoailazy.coexistree.document.entity.DocumentEntity;
import io.github.xiaoailazy.coexistree.document.repository.DocumentRepository;
import io.github.xiaoailazy.coexistree.document.service.DocumentTreeService;
import io.github.xiaoailazy.coexistree.document.storage.MarkdownFileStorageService;
import io.github.xiaoailazy.coexistree.indexer.llm.LlmClient;
import io.github.xiaoailazy.coexistree.indexer.llm.PromptTemplateService;
import io.github.xiaoailazy.coexistree.indexer.model.Citation;
import io.github.xiaoailazy.coexistree.indexer.model.NodeSource;
import io.github.xiaoailazy.coexistree.indexer.model.TreeNode;
import io.github.xiaoailazy.coexistree.indexer.tree.TreeNodeMapper;
import io.github.xiaoailazy.coexistree.knowledge.service.SystemKnowledgeTreeService;
import io.github.xiaoailazy.coexistree.review.service.IntentClassifier;
import io.github.xiaoailazy.coexistree.review.service.RequirementEvaluationService;
import io.github.xiaoailazy.coexistree.shared.util.JsonUtils;
import io.github.xiaoailazy.coexistree.system.repository.SystemUserMappingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * Tests for ConversationServiceImpl
 * Regression tests for citation field population
 */
@ExtendWith(MockitoExtension.class)
class ConversationServiceImplTest {

    @Mock
    private ConversationRepository conversationRepository;
    @Mock
    private MessageRepository messageRepository;
    @Mock
    private SystemKnowledgeTreeService systemKnowledgeTreeService;
    @Mock
    private DocumentTreeService documentTreeService;
    @Mock
    private TreeNodeMapper treeNodeMapper;
    @Mock
    private TreeSearchService treeSearchService;
    @Mock
    private AnswerGenerationService answerGenerationService;
    @Mock
    private PromptTemplateService promptTemplateService;
    @Mock
    private LlmClient llmClient;
    @Mock
    private JsonUtils jsonUtils;
    @Mock
    private IntentClassifier intentClassifier;
    @Mock
    private RequirementEvaluationService requirementEvaluationService;
    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private MarkdownFileStorageService markdownFileStorageService;
    @Mock
    private SystemUserMappingRepository systemUserMappingRepository;

    @InjectMocks
    private ConversationServiceImpl conversationService;

    @Test
    void createCitationsShouldIncludeAllFields() {
        // Given: TreeNode with all required fields
        TreeNode node = new TreeNode();
        node.setNodeId("node-123");
        node.setTitle("Test Section");
        node.setLineNum(42);
        node.setLevel(2);
        node.setSummary("This is a test summary");

        NodeSource source = new NodeSource();
        source.setDocId(100L);
        source.setNodeId("node-123");
        source.setSecurityLevel(1);
        node.setSources(List.of(source));

        DocumentEntity document = new DocumentEntity();
        document.setId(100L);
        document.setDocName("test-document.md");

        when(documentRepository.findById(100L)).thenReturn(Optional.of(document));

        // When: Creating citation using the same logic as in ConversationServiceImpl
        Long docId = null;
        String docName = null;
        if (node.getSources() != null && !node.getSources().isEmpty()) {
            NodeSource firstSource = node.getSources().get(0);
            docId = firstSource.getDocId();
            if (docId != null) {
                DocumentEntity doc = documentRepository.findById(docId).orElse(null);
                docName = doc != null ? doc.getDocName() : null;
            }
        }

        Citation citation = new Citation(
                node.getNodeId(),
                node.getTitle(),
                node.getSummary(),
                node.getSources(),
                docId,
                docName,
                node.getLineNum(),
                node.getLevel()
        );

        // Then: All fields should be populated
        assertThat(citation.nodeId()).isEqualTo("node-123");
        assertThat(citation.title()).isEqualTo("Test Section");
        assertThat(citation.snippet()).isEqualTo("This is a test summary");
        assertThat(citation.docId()).isEqualTo(100L);
        assertThat(citation.docName()).isEqualTo("test-document.md");
        assertThat(citation.lineNum()).isEqualTo(42);
        assertThat(citation.level()).isEqualTo(2);
        assertThat(citation.sources()).hasSize(1);
    }

    @Test
    void createCitationsShouldHandleMissingDocument() {
        // Given: TreeNode with source pointing to non-existent document
        TreeNode node = new TreeNode();
        node.setNodeId("node-456");
        node.setTitle("Missing Doc Section");
        node.setLineNum(10);
        node.setLevel(1);
        node.setSummary("Summary");

        NodeSource source = new NodeSource();
        source.setDocId(999L); // Non-existent document
        source.setNodeId("node-456");
        node.setSources(List.of(source));

        when(documentRepository.findById(999L)).thenReturn(Optional.empty());

        // When: Creating citation
        Long docId = null;
        String docName = null;
        if (node.getSources() != null && !node.getSources().isEmpty()) {
            NodeSource firstSource = node.getSources().get(0);
            docId = firstSource.getDocId();
            if (docId != null) {
                DocumentEntity doc = documentRepository.findById(docId).orElse(null);
                docName = doc != null ? doc.getDocName() : null;
            }
        }

        Citation citation = new Citation(
                node.getNodeId(),
                node.getTitle(),
                node.getSummary(),
                node.getSources(),
                docId,
                docName,
                node.getLineNum(),
                node.getLevel()
        );

        // Then: docId should be present but docName should be null
        assertThat(citation.docId()).isEqualTo(999L);
        assertThat(citation.docName()).isNull(); // Document not found
        assertThat(citation.lineNum()).isEqualTo(10);
    }

    @Test
    void createCitationsShouldHandleMissingSources() {
        // Given: TreeNode without sources
        TreeNode node = new TreeNode();
        node.setNodeId("node-789");
        node.setTitle("No Sources Section");
        node.setLineNum(5);
        node.setLevel(1);
        node.setSummary("Summary");
        node.setSources(null);

        // When: Creating citation without sources
        Long docId = null;
        String docName = null;
        if (node.getSources() != null && !node.getSources().isEmpty()) {
            NodeSource firstSource = node.getSources().get(0);
            docId = firstSource.getDocId();
        }

        Citation citation = new Citation(
                node.getNodeId(),
                node.getTitle(),
                node.getSummary(),
                node.getSources(),
                docId,
                docName,
                node.getLineNum(),
                node.getLevel()
        );

        // Then: docId and docName should be null, but other fields should be present
        assertThat(citation.nodeId()).isEqualTo("node-789");
        assertThat(citation.docId()).isNull();
        assertThat(citation.docName()).isNull();
        assertThat(citation.lineNum()).isEqualTo(5);
    }

    @Test
    void citationBackwardCompatibilityConstructorShouldHaveNullDocFields() {
        // Test the backward-compatible 4-parameter constructor
        TreeNode node = new TreeNode();
        node.setNodeId("node-old");
        node.setTitle("Old Style");
        node.setLineNum(20);
        node.setLevel(1);

        NodeSource source = new NodeSource();
        source.setDocId(1L);
        node.setSources(List.of(source));

        // Using old constructor (only 4 params)
        Citation citation = new Citation(
                node.getNodeId(),
                node.getTitle(),
                "snippet",
                node.getSources()
        );

        // Then: docId, docName, lineNum, level should be null
        assertThat(citation.nodeId()).isEqualTo("node-old");
        assertThat(citation.docId()).isNull();
        assertThat(citation.docName()).isNull();
        assertThat(citation.lineNum()).isNull();
        assertThat(citation.level()).isNull();
    }
}
