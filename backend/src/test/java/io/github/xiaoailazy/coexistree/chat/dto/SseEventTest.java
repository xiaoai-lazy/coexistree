package io.github.xiaoailazy.coexistree.chat.dto;

import io.github.xiaoailazy.coexistree.indexer.model.Citation;
import io.github.xiaoailazy.coexistree.indexer.model.NodeSource;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for SseEvent and CitationDto
 * Regression tests for citation field mapping
 */
class SseEventTest {

    @Test
    void citationDtoFromShouldMapAllFields() {
        // Given: Citation with all fields populated
        NodeSource source = new NodeSource();
        source.setDocId(100L);
        source.setNodeId("source-node-1");
        source.setSecurityLevel(1);

        Citation citation = new Citation(
                "node-123",
                "Test Title",
                "Test snippet content",
                List.of(source),
                100L,
                "test-document.md",
                42,
                2
        );

        // When: Converting to DTO
        SseEvent.CitationDto dto = SseEvent.CitationDto.from(citation);

        // Then: All fields should be correctly mapped
        assertThat(dto.path()).contains("node-123").contains("Test Title");
        assertThat(dto.text()).isEqualTo("Test snippet content");
        assertThat(dto.docId()).isEqualTo(100L);
        assertThat(dto.docName()).isEqualTo("test-document.md");
        assertThat(dto.nodeId()).isEqualTo("node-123");
        assertThat(dto.lineNum()).isEqualTo(42);
        assertThat(dto.level()).isEqualTo(2);
    }

    @Test
    void citationDtoFromShouldHandleNullFields() {
        // Given: Citation with null doc fields (backward compatible)
        Citation citation = new Citation(
                "node-456",
                "Title",
                "Snippet",
                null,
                null,
                null,
                null,
                null
        );

        // When: Converting to DTO
        SseEvent.CitationDto dto = SseEvent.CitationDto.from(citation);

        // Then: DTO should have null fields but not throw NPE
        assertThat(dto.path()).contains("node-456");
        assertThat(dto.text()).isEqualTo("Snippet");
        assertThat(dto.docId()).isNull();
        assertThat(dto.docName()).isNull();
        assertThat(dto.nodeId()).isEqualTo("node-456");
        assertThat(dto.lineNum()).isNull();
        assertThat(dto.level()).isNull();
    }

    @Test
    void citationDtoFromShouldIncludeSourceInfoInPath() {
        // Given: Citation with sources
        NodeSource source1 = new NodeSource();
        source1.setDocId(1L);
        source1.setNodeId("src-1");

        NodeSource source2 = new NodeSource();
        source2.setDocId(2L);
        source2.setNodeId("src-2");

        Citation citation = new Citation(
                "main-node",
                "Main Title",
                "Content",
                List.of(source1, source2),
                1L,
                "doc.md",
                10,
                1
        );

        // When: Converting to DTO
        SseEvent.CitationDto dto = SseEvent.CitationDto.from(citation);

        // Then: Path should include source information
        assertThat(dto.path()).contains("main-node");
        assertThat(dto.path()).contains("Main Title");
        assertThat(dto.path()).contains("doc:1");
        assertThat(dto.path()).contains("node:src-1");
        assertThat(dto.path()).contains("doc:2");
        assertThat(dto.path()).contains("node:src-2");
    }

    @Test
    void citationsEventShouldContainAllDtoFields() {
        // Given: Multiple citations
        Citation citation1 = new Citation(
                "node-1", "Title 1", "Snippet 1",
                null, 1L, "doc1.md", 10, 1
        );
        Citation citation2 = new Citation(
                "node-2", "Title 2", "Snippet 2",
                null, 2L, "doc2.md", 20, 2
        );

        // When: Creating citations event
        SseEvent event = SseEvent.citations(List.of(citation1, citation2));

        // Then: Event should contain all citations with all fields
        assertThat(event.type()).isEqualTo("citations");
        assertThat(event.citations()).hasSize(2);

        SseEvent.CitationDto dto1 = event.citations().get(0);
        assertThat(dto1.docId()).isEqualTo(1L);
        assertThat(dto1.docName()).isEqualTo("doc1.md");
        assertThat(dto1.lineNum()).isEqualTo(10);

        SseEvent.CitationDto dto2 = event.citations().get(1);
        assertThat(dto2.docId()).isEqualTo(2L);
        assertThat(dto2.docName()).isEqualTo("doc2.md");
        assertThat(dto2.lineNum()).isEqualTo(20);
    }

    @Test
    void citationWithNullDocIdShouldNotBeClickable() {
        // Given: Citation without docId (backward compatible data)
        Citation citation = new Citation(
                "old-node",
                "Old Data",
                "Old snippet",
                null,  // sources
                null,  // docId
                null,  // docName
                null,  // lineNum
                null   // level
        );

        // When: Converting to DTO
        SseEvent.CitationDto dto = SseEvent.CitationDto.from(citation);

        // Then: Frontend should detect this as non-clickable
        // (docId is null, so :class="{ 'clickable': cite.docId }" will be false)
        assertThat(dto.docId()).isNull();
        assertThat(dto.lineNum()).isNull();
    }

    @Test
    void stageEventShouldNotHaveCitations() {
        // When: Creating stage event
        SseEvent event = SseEvent.stage("searching", "processing");

        // Then: Citations should be null
        assertThat(event.type()).isEqualTo("stage");
        assertThat(event.citations()).isNull();
    }

    @Test
    void thinkingEventShouldNotHaveCitations() {
        // When: Creating thinking event
        SseEvent event = SseEvent.thinking("Thinking content");

        // Then: Citations should be null
        assertThat(event.type()).isEqualTo("thinking");
        assertThat(event.citations()).isNull();
    }

    @Test
    void answerEventShouldNotHaveCitations() {
        // When: Creating answer event
        SseEvent event = SseEvent.answer("Answer content");

        // Then: Citations should be null
        assertThat(event.type()).isEqualTo("answer");
        assertThat(event.citations()).isNull();
    }
}
