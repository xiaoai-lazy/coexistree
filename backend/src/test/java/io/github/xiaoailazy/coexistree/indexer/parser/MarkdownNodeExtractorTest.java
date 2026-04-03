package io.github.xiaoailazy.coexistree.indexer.parser;

import io.github.xiaoailazy.coexistree.indexer.model.RawHeaderNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MarkdownNodeExtractor 测试")
class MarkdownNodeExtractorTest {

    private final MarkdownNodeExtractor extractor = new MarkdownNodeExtractor();

    @Nested
    @DisplayName("基本提取功能测试")
    class BasicExtractionTests {

        @Test
        @DisplayName("提取各级标题（H1-H6）")
        void shouldExtractAllHeaderLevels() {
            String markdown = """
                    # H1 Title
                    ## H2 Title
                    ### H3 Title
                    #### H4 Title
                    ##### H5 Title
                    ###### H6 Title
                    """;

            List<RawHeaderNode> result = extractor.extract(markdown);

            assertThat(result)
                    .extracting(RawHeaderNode::nodeTitle)
                    .containsExactly("H1 Title", "H2 Title", "H3 Title", "H4 Title", "H5 Title", "H6 Title");
        }

        @Test
        @DisplayName("忽略代码块中的标题")
        void shouldIgnoreHeadersInCodeBlocks() {
            String markdown = """
                    # Root
                    intro

                    ```java
                    # This is code comment
                    ## Also code
                    ```

                    ## Child
                    body
                    """;

            List<RawHeaderNode> result = extractor.extract(markdown);

            assertThat(result)
                    .extracting(RawHeaderNode::nodeTitle)
                    .containsExactly("Root", "Child");
        }

        @Test
        @DisplayName("正确处理多行代码块")
        void shouldHandleMultiLineCodeBlocks() {
            String markdown = """
                    # Before Code

                    ```python
                    def test():
                        # This is a comment
                        pass

                    ## Not a header
                    ```

                    # After Code
                    """;

            List<RawHeaderNode> result = extractor.extract(markdown);

            assertThat(result)
                    .extracting(RawHeaderNode::nodeTitle)
                    .containsExactly("Before Code", "After Code");
        }
    }

    @Nested
    @DisplayName("边界情况测试")
    class EdgeCaseTests {

        @Test
        @DisplayName("空字符串返回空列表")
        void shouldReturnEmptyListForEmptyString() {
            List<RawHeaderNode> result = extractor.extract("");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("纯空白内容返回空列表")
        void shouldReturnEmptyListForWhitespaceOnly() {
            String markdown = """


                           \n\t\n
                    """;

            List<RawHeaderNode> result = extractor.extract(markdown);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("无标题内容返回空列表")
        void shouldReturnEmptyListWhenNoHeaders() {
            String markdown = """
                    This is just plain text.
                    No headers here.

                    - List item 1
                    - List item 2
                    """;

            List<RawHeaderNode> result = extractor.extract(markdown);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("忽略超过6级的标题")
        void shouldIgnoreHeadersBeyondLevel6() {
            String markdown = """
                    # Valid H1
                    ####### Invalid H7
                    ## Valid H2
                    ######## Invalid H8
                    """;

            List<RawHeaderNode> result = extractor.extract(markdown);

            assertThat(result)
                    .extracting(RawHeaderNode::nodeTitle)
                    .containsExactly("Valid H1", "Valid H2");
        }
    }

    @Nested
    @DisplayName("标题格式测试")
    class HeaderFormatTests {

        @Test
        @DisplayName("正确处理标题空格")
        void shouldHandleHeadersWithSpaces() {
            String markdown = """
                    # Normal Space
                    ##  Multiple   Spaces
                    ###	TabBefore
                    """;

            List<RawHeaderNode> result = extractor.extract(markdown);

            assertThat(result)
                    .extracting(RawHeaderNode::nodeTitle)
                    .containsExactly("Normal Space", "Multiple   Spaces", "TabBefore");
        }

        @Test
        @DisplayName("忽略不带空格的标题")
        void shouldIgnoreHeadersWithoutSpace() {
            String markdown = """
                    #NotAHeader
                    # Valid Header
                    ##AlsoNot
                    """;

            List<RawHeaderNode> result = extractor.extract(markdown);

            // #NotAHeader 和 ##AlsoNot 不符合标题格式（#后需要空格）
            assertThat(result)
                    .extracting(RawHeaderNode::nodeTitle)
                    .containsExactly("Valid Header");
        }

        @Test
        @DisplayName("正确计算行号")
        void shouldCalculateCorrectLineNumbers() {
            String markdown = """
                    Line 1
                    # Line 2
                    Line 3
                    ## Line 4
                    Line 5
                    ### Line 6
                    """;

            List<RawHeaderNode> result = extractor.extract(markdown);

            assertThat(result)
                    .extracting(RawHeaderNode::lineNum)
                    .containsExactly(2, 4, 6);
        }

        @Test
        @DisplayName("保留标题中的特殊字符")
        void shouldPreserveSpecialCharactersInHeaders() {
            String markdown = """
                    # Title with 中文
                    ## Title with emoji 🎉
                    ### Title with `code` inline
                    #### Title with "quotes" and 'apostrophes'
                    """;

            List<RawHeaderNode> result = extractor.extract(markdown);

            assertThat(result)
                    .extracting(RawHeaderNode::nodeTitle)
                    .containsExactly(
                            "Title with 中文",
                            "Title with emoji 🎉",
                            "Title with `code` inline",
                            "Title with \"quotes\" and 'apostrophes'"
                    );
        }
    }

    @Nested
    @DisplayName("复杂场景测试")
    class ComplexScenarioTests {

        @Test
        @DisplayName("正确处理嵌套代码块")
        void shouldHandleNestedCodeBlocks() {
            String markdown = """
                    # Root

                    ```markdown
                    ## Inside markdown code block
                    ```

                    ## Real Section

                    ```java
                    // ## Another fake header
                    ```

                    ### Subsection
                    """;

            List<RawHeaderNode> result = extractor.extract(markdown);

            assertThat(result)
                    .extracting(RawHeaderNode::nodeTitle)
                    .containsExactly("Root", "Real Section", "Subsection");
        }

        @Test
        @DisplayName("处理行内代码中的井号")
        void shouldHandleHashesInInlineCode() {
            String markdown = """
                    # Real Header

                    Here is some text with `## inline code` that looks like header.

                    ## Another Real Header
                    """;

            List<RawHeaderNode> result = extractor.extract(markdown);

            assertThat(result)
                    .extracting(RawHeaderNode::nodeTitle)
                    .containsExactly("Real Header", "Another Real Header");
        }

        @Test
        @DisplayName("处理不闭合的代码块")
        void shouldHandleUnclosedCodeBlock() {
            String markdown = """
                    # Before

                    ```java
                    // No closing backticks
                    # This should be ignored

                    ## After
                    """;

            List<RawHeaderNode> result = extractor.extract(markdown);

            // 不闭合的代码块会导致后续内容被视为在代码块内
            // 这是预期行为
            assertThat(result)
                    .extracting(RawHeaderNode::nodeTitle)
                    .containsExactly("Before");
        }

        @Test
        @DisplayName("处理多个代码块交替出现")
        void shouldHandleAlternatingCodeBlocks() {
            String markdown = """
                    # Section 1

                    ```code1
                    # fake 1
                    ```

                    ## Section 2

                    ```code2
                    # fake 2
                    ```

                    ### Section 3
                    """;

            List<RawHeaderNode> result = extractor.extract(markdown);

            assertThat(result)
                    .extracting(RawHeaderNode::nodeTitle)
                    .containsExactly("Section 1", "Section 2", "Section 3");
        }
    }
}
