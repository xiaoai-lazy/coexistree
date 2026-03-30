package io.github.xiaoailazy.coexistree.indexer.parser;

import io.github.xiaoailazy.coexistree.indexer.model.RawHeaderNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MarkdownNodeExtractorTest {

    private final MarkdownNodeExtractor extractor = new MarkdownNodeExtractor();

    @Test
    void shouldExtractHeadersAndIgnoreCodeBlocks() {
        String markdown = """
                # Root
                intro

                ```java
                # ignored
                ```

                ## Child
                body
                """;

        List<RawHeaderNode> result = extractor.extract(markdown);

        assertThat(result)
                .extracting(RawHeaderNode::nodeTitle)
                .containsExactly("Root", "Child");
        assertThat(result)
                .extracting(RawHeaderNode::lineNum)
                .containsExactly(1, 8);
    }
}
