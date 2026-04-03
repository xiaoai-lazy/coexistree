package io.github.xiaoailazy.coexistree.indexer.facade;

import io.github.xiaoailazy.coexistree.indexer.model.DocumentTree;
import io.github.xiaoailazy.coexistree.indexer.model.PageIndexBuildOptions;
import io.github.xiaoailazy.coexistree.indexer.parser.MarkdownNodeExtractor;
import io.github.xiaoailazy.coexistree.indexer.parser.MarkdownNodeTextAssembler;
import io.github.xiaoailazy.coexistree.indexer.llm.LlmClient;
import io.github.xiaoailazy.coexistree.indexer.summary.DocumentSummaryService;
import io.github.xiaoailazy.coexistree.indexer.summary.NodeSummaryService;
import io.github.xiaoailazy.coexistree.indexer.tree.PageIndexTreeBuilder;
import io.github.xiaoailazy.coexistree.indexer.tree.TreeNodeCounter;
import io.github.xiaoailazy.coexistree.shared.test.LlmMockFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import static org.assertj.core.api.Assertions.assertThat;

class PageIndexMarkdownServiceTest {

    private final LlmClient llmClient = Mockito.mock(LlmClient.class);

    private final PageIndexMarkdownService service = new PageIndexMarkdownService(
            new MarkdownNodeExtractor(),
            new MarkdownNodeTextAssembler(),
            new PageIndexTreeBuilder(),
            new NodeSummaryService(llmClient),
            new DocumentSummaryService(llmClient, new io.github.xiaoailazy.coexistree.shared.util.JsonUtils(new io.github.xiaoailazy.coexistree.config.JacksonConfig().objectMapper())),
            new TreeNodeCounter()
    );

    private final Path testRoot = Path.of("target", "test-work", "pageindex");

    @AfterEach
    void cleanup() throws IOException {
        if (Files.notExists(testRoot)) {
            return;
        }
        try (var walk = Files.walk(testRoot)) {
            walk.sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                        }
                    });
        }
    }

    @Test
    void shouldBuildDocumentTreeWithHierarchyAndSummary() throws Exception {
        LlmMockFactory.mockForDocumentSummary(llmClient, "Document tree for system-guide");

        Files.createDirectories(testRoot);
        Path markdown = testRoot.resolve("system-guide.md");
        Files.writeString(markdown, """
                # Overview
                Overview details.

                ## Install
                Install steps.

                # FAQ
                FAQ details.
                """);

        DocumentTree tree = service.buildTree(markdown, PageIndexBuildOptions.defaultOptions("test-model"));

        assertThat(tree.getDocName()).isEqualTo("system-guide");
        assertThat(tree.getDocDescription()).isEqualTo("Document tree for system-guide");
        assertThat(tree.getStructure()).hasSize(2);
        assertThat(tree.getStructure().get(0).getTitle()).isEqualTo("Overview");
        assertThat(tree.getStructure().get(0).getNodes()).hasSize(1);
        assertThat(tree.getStructure().get(0).getNodes().get(0).getTitle()).isEqualTo("Install");
        assertThat(tree.getStructure().get(0).getPrefixSummary()).contains("Overview");
        assertThat(tree.getStructure().get(0).getSummary()).isNull();
        assertThat(tree.getStructure().get(0).getNodes().get(0).getSummary()).contains("Install");
        assertThat(tree.getStructure().get(0).getNodeId()).isEqualTo("0001");
        assertThat(tree.getStructure().get(1).getNodeId()).isEqualTo("0003");
        assertThat(tree.getStructure().get(0).getLevel()).isNull();
    }

    @Test
    void shouldRespectThinningAndOutputOptions() throws Exception {
        // Mock LLM 调用 - 生成节点摘要
        LlmMockFactory.mockForNodeSummary(llmClient, "父节点摘要内容");

        Files.createDirectories(testRoot);
        Path markdown = testRoot.resolve("thin.md");
        Files.writeString(markdown, """
                # Parent
                Parent intro.

                ## Child One
                Child one details.

                ## Child Two
                Child two details.
                """);

        PageIndexBuildOptions options = new PageIndexBuildOptions(
                true,
                100,
                true,
                200,
                false,
                false,
                false,
                "test-model"
        );

        DocumentTree tree = service.buildTree(markdown, options);

        assertThat(tree.getStructure()).hasSize(1);
        assertThat(tree.getStructure().get(0).getNodes()).isEmpty();
        assertThat(tree.getStructure().get(0).getText()).isNull();
        assertThat(tree.getStructure().get(0).getNodeId()).isNull();
        assertThat(tree.getStructure().get(0).getSummary()).contains("Parent");
        assertThat(tree.getStructure().get(0).getPrefixSummary()).isNull();
        assertThat(tree.getDocDescription()).isNull();
    }
}
