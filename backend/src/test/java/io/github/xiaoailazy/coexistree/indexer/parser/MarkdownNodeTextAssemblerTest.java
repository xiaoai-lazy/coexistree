package io.github.xiaoailazy.coexistree.indexer.parser;

import io.github.xiaoailazy.coexistree.indexer.model.FlatMarkdownNode;
import io.github.xiaoailazy.coexistree.indexer.model.RawHeaderNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MarkdownNodeTextAssemblerTest {

    private final MarkdownNodeTextAssembler assembler = new MarkdownNodeTextAssembler();

    @Test
    void shouldAssembleNodesWithText() {
        List<RawHeaderNode> headers = List.of(
                new RawHeaderNode("Overview", 1),
                new RawHeaderNode("Install", 3),
                new RawHeaderNode("FAQ", 5)
        );
        List<String> lines = List.of(
                "# Overview",
                "Overview content.",
                "## Install",
                "Install steps.",
                "# FAQ",
                "FAQ content."
        );

        List<FlatMarkdownNode> result = assembler.assemble(headers, lines);

        assertThat(result).hasSize(3);
        
        assertThat(result.get(0).getTitle()).isEqualTo("Overview");
        assertThat(result.get(0).getLevel()).isEqualTo(1);
        assertThat(result.get(0).getLineNum()).isEqualTo(1);
        assertThat(result.get(0).getText()).isEqualTo("# Overview\nOverview content.");

        assertThat(result.get(1).getTitle()).isEqualTo("Install");
        assertThat(result.get(1).getLevel()).isEqualTo(2);
        assertThat(result.get(1).getLineNum()).isEqualTo(3);
        assertThat(result.get(1).getText()).isEqualTo("## Install\nInstall steps.");

        assertThat(result.get(2).getTitle()).isEqualTo("FAQ");
        assertThat(result.get(2).getLevel()).isEqualTo(1);
        assertThat(result.get(2).getLineNum()).isEqualTo(5);
        assertThat(result.get(2).getText()).isEqualTo("# FAQ\nFAQ content.");
    }

    @Test
    void shouldHandleSingleNode() {
        List<RawHeaderNode> headers = List.of(
                new RawHeaderNode("Title", 1)
        );
        List<String> lines = List.of(
                "# Title",
                "Content line 1",
                "Content line 2"
        );

        List<FlatMarkdownNode> result = assembler.assemble(headers, lines);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTitle()).isEqualTo("Title");
        assertThat(result.get(0).getLevel()).isEqualTo(1);
        assertThat(result.get(0).getText()).isEqualTo("# Title\nContent line 1\nContent line 2");
    }

    @Test
    void shouldHandleEmptyHeaders() {
        List<RawHeaderNode> headers = List.of();
        List<String> lines = List.of("Some content");

        List<FlatMarkdownNode> result = assembler.assemble(headers, lines);

        assertThat(result).isEmpty();
    }

    @Test
    void shouldSkipHeaderWithInvalidLine() {
        List<RawHeaderNode> headers = List.of(
                new RawHeaderNode("Invalid", 1)
        );
        List<String> lines = List.of("Not a header");

        List<FlatMarkdownNode> result = assembler.assemble(headers, lines);

        assertThat(result).isEmpty();
    }

    @Test
    void shouldHandleMultipleLevels() {
        List<RawHeaderNode> headers = List.of(
                new RawHeaderNode("H1", 1),
                new RawHeaderNode("H2", 2),
                new RawHeaderNode("H3", 3)
        );
        List<String> lines = List.of(
                "# H1",
                "## H2",
                "### H3"
        );

        List<FlatMarkdownNode> result = assembler.assemble(headers, lines);

        assertThat(result).hasSize(3);
        assertThat(result.get(0).getLevel()).isEqualTo(1);
        assertThat(result.get(1).getLevel()).isEqualTo(2);
        assertThat(result.get(2).getLevel()).isEqualTo(3);
    }
}
