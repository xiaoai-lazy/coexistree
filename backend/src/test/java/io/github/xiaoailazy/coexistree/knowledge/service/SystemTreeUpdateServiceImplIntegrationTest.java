package io.github.xiaoailazy.coexistree.knowledge.service;

import io.github.xiaoailazy.coexistree.change.entity.SystemChangeRecordEntity;
import io.github.xiaoailazy.coexistree.change.repository.SystemChangeRecordRepository;
import io.github.xiaoailazy.coexistree.document.entity.DocumentEntity;
import io.github.xiaoailazy.coexistree.document.repository.DocumentRepository;
import io.github.xiaoailazy.coexistree.document.tree.DocumentTreeBuildService;
import io.github.xiaoailazy.coexistree.knowledge.entity.SystemKnowledgeTreeEntity;
import io.github.xiaoailazy.coexistree.knowledge.plan.SystemTreePlanService;
import io.github.xiaoailazy.coexistree.knowledge.repository.SystemKnowledgeTreeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@Sql(scripts = "/sql/base-test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(scripts = "/sql/cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class SystemTreeUpdateServiceImplIntegrationTest {

    @MockBean
    private DocumentTreeBuildService documentTreeBuildService;

    @MockBean
    private SystemTreePlanService planService;

    @Autowired
    private SystemTreeUpdateService systemTreeUpdateService;

    @Autowired
    private SystemChangeRecordRepository changeRecordRepository;

    @Autowired
    private SystemKnowledgeTreeRepository systemKnowledgeTreeRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @BeforeEach
    void stubPlanAndBuild() {
        doNothing().when(documentTreeBuildService).buildDocumentTreesForChange(anyLong());
        when(planService.generateUpdatePlanJson(anyString()))
                .thenAnswer(
                        inv -> {
                            String p = inv.getArgument(0);
                            long id = Long.parseLong(p.substring("changeRecord:".length()));
                            return String.format(
                                    "{\"changeRecordId\":%d,\"baseTreeVersion\":1,\"operations\":[]}", id);
                        });
    }

    private static String loadMinimalActiveTree() throws IOException {
        try (InputStream in =
                SystemTreeUpdateServiceImplIntegrationTest.class.getResourceAsStream(
                        "/featuretree/golden/minimal-active-tree.json")) {
            assertThat(in).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void idempotentWhenAlreadyApplied() {
        SystemChangeRecordEntity cr = new SystemChangeRecordEntity();
        cr.setSystemId(1L);
        cr.setTitle("done");
        cr.setCreatedBy(101L);
        cr.setCreatedAt(LocalDateTime.now());
        cr.setUpdatedAt(LocalDateTime.now());
        cr.setTreeVersionAfter(2);
        cr = changeRecordRepository.save(cr);

        systemTreeUpdateService.applyChange(cr.getId());

        verify(planService, never()).generateUpdatePlanJson(anyString());
        verify(documentTreeBuildService, never()).buildDocumentTreesForChange(anyLong());
    }

    @Test
    void applyChangeUpdatesActiveTreeVersion() throws Exception {
        String treeJson = loadMinimalActiveTree();

        SystemKnowledgeTreeEntity tree = new SystemKnowledgeTreeEntity();
        tree.setSystemId(1L);
        tree.setTreeJson(treeJson);
        tree.setTreeVersion(1);
        tree.setNodeCount(3);
        tree.setTreeStatus("ACTIVE");
        tree.setCreatedAt(LocalDateTime.now());
        tree.setUpdatedAt(LocalDateTime.now());
        systemKnowledgeTreeRepository.save(tree);

        SystemChangeRecordEntity cr = new SystemChangeRecordEntity();
        cr.setSystemId(1L);
        cr.setTitle("open");
        cr.setCreatedBy(101L);
        cr.setCreatedAt(LocalDateTime.now());
        cr.setUpdatedAt(LocalDateTime.now());
        cr = changeRecordRepository.save(cr);

        DocumentEntity doc = new DocumentEntity();
        doc.setSystemId(1L);
        doc.setChangeRecordId(cr.getId());
        doc.setDocName("req.md");
        doc.setOriginalFileName("req.md");
        doc.setContentType("markdown");
        doc.setParseStatus("SUCCESS");
        doc.setDocType("BASELINE");
        doc.setDocContentType("REQUIREMENT");
        doc.setFileContent("# body");
        doc.setMarkdownContent("# body");
        doc.setContentHash("deadbeef");
        doc.setTreeBuildStatus("SUCCESS");
        doc.setMergeStatus("PENDING");
        doc.setSecurityLevel(1);
        doc.setUploadedBy(101L);
        doc.setCreatedAt(LocalDateTime.now());
        doc.setUpdatedAt(LocalDateTime.now());
        documentRepository.save(doc);

        systemTreeUpdateService.applyChange(cr.getId());

        SystemKnowledgeTreeEntity updated =
                systemKnowledgeTreeRepository.findBySystemIdAndTreeStatus(1L, "ACTIVE").orElseThrow();
        assertThat(updated.getTreeVersion()).isEqualTo(2);

        SystemChangeRecordEntity after = changeRecordRepository.findById(cr.getId()).orElseThrow();
        assertThat(after.getTreeVersionAfter()).isEqualTo(2);
        assertThat(after.getTreeVersionBefore()).isEqualTo(1);

        verify(documentTreeBuildService).buildDocumentTreesForChange(cr.getId());
        verify(planService).generateUpdatePlanJson(anyString());
    }
}
