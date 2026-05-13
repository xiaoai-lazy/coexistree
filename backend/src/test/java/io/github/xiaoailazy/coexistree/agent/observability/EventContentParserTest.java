package io.github.xiaoailazy.coexistree.agent.observability;

import com.google.adk.events.Event;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for EventContentParser — extracts thinking and answer text from ADK Events.
 *
 * Technical premise: ADK Event.content().parts() contains Part objects where
 * Part.thought() returns Optional<Boolean>. When thought() is true, the Part
 * contains thinking content; otherwise it contains answer content.
 */
class EventContentParserTest {

    @Test
    @DisplayName("detects thinking when event has a Part with thought=true")
    void shouldDetectThinkingPart() {
        // Given: Event with a thinking Part
        Part thinkingPart = Part.builder()
                .text("Let me think about this...")
                .thought(true)
                .build();
        Content content = Content.fromParts(thinkingPart);
        Event event = Event.builder()
                .content(content)
                .partial(true)
                .build();

        // When: Parsing the event
        EventContentParser.ParsedContent parsed = EventContentParser.parse(event);

        // Then: thinking text should be extracted
        assertThat(parsed.thinkingText()).isEqualTo("Let me think about this...");
        assertThat(parsed.answerText()).isEmpty();
        assertThat(parsed.hasThinking()).isTrue();
    }

    @Test
    @DisplayName("detects answer when event has a Part without thought flag")
    void shouldDetectAnswerPart() {
        // Given: Event with a normal (answer) Part
        Part answerPart = Part.builder()
                .text("The answer is 42.")
                .build();
        Content content = Content.fromParts(answerPart);
        Event event = Event.builder()
                .content(content)
                .partial(true)
                .build();

        // When: Parsing the event
        EventContentParser.ParsedContent parsed = EventContentParser.parse(event);

        // Then: answer text should be extracted, no thinking
        assertThat(parsed.answerText()).isEqualTo("The answer is 42.");
        assertThat(parsed.thinkingText()).isEmpty();
        assertThat(parsed.hasThinking()).isFalse();
    }

    @Test
    @DisplayName("handles mixed event with both thinking and answer parts")
    void shouldSeparateThinkingAndAnswerFromMixedParts() {
        // Given: Event with thinking + answer Parts
        Part thinkingPart = Part.builder()
                .text("Hmm, let me analyze...")
                .thought(true)
                .build();
        Part answerPart = Part.builder()
                .text("Based on the analysis, the result is clear.")
                .build();
        Content content = Content.fromParts(thinkingPart, answerPart);
        Event event = Event.builder()
                .content(content)
                .partial(false)
                .build();

        // When: Parsing the event
        EventContentParser.ParsedContent parsed = EventContentParser.parse(event);

        // Then: both should be extracted separately
        assertThat(parsed.thinkingText()).isEqualTo("Hmm, let me analyze...");
        assertThat(parsed.answerText()).isEqualTo("Based on the analysis, the result is clear.");
        assertThat(parsed.hasThinking()).isTrue();
    }

    @Test
    @DisplayName("handles event with no content")
    void shouldHandleNoContent() {
        // Given: Event without content (e.g., function call event)
        Event event = Event.builder().build();

        // When: Parsing the event
        EventContentParser.ParsedContent parsed = EventContentParser.parse(event);

        // Then: both should be empty
        assertThat(parsed.thinkingText()).isEmpty();
        assertThat(parsed.answerText()).isEmpty();
        assertThat(parsed.hasThinking()).isFalse();
    }

    @Test
    @DisplayName("handles event with empty parts list")
    void shouldHandleEmptyParts() {
        // Given: Event with empty content
        Content content = Content.builder().parts(List.of()).build();
        Event event = Event.builder().content(content).build();

        // When: Parsing the event
        EventContentParser.ParsedContent parsed = EventContentParser.parse(event);

        // Then: both should be empty
        assertThat(parsed.thinkingText()).isEmpty();
        assertThat(parsed.answerText()).isEmpty();
        assertThat(parsed.hasThinking()).isFalse();
    }

    @Test
    @DisplayName("handles event with null parts")
    void shouldHandleNullParts() {
        // Given: Event with null parts
        Content content = Content.builder().build();
        Event event = Event.builder().content(content).build();

        // When: Parsing the event
        EventContentParser.ParsedContent parsed = EventContentParser.parse(event);

        // Then: both should be empty
        assertThat(parsed.thinkingText()).isEmpty();
        assertThat(parsed.answerText()).isEmpty();
        assertThat(parsed.hasThinking()).isFalse();
    }

    @Test
    @DisplayName("concatenates multiple thinking parts")
    void shouldConcatenateMultipleThinkingParts() {
        // Given: Event with multiple thinking parts
        Part t1 = Part.builder().text("First thought ").thought(true).build();
        Part t2 = Part.builder().text("Second thought").thought(true).build();
        Content content = Content.fromParts(t1, t2);
        Event event = Event.builder().content(content).build();

        // When: Parsing
        EventContentParser.ParsedContent parsed = EventContentParser.parse(event);

        // Then: thinking should be concatenated
        assertThat(parsed.thinkingText()).isEqualTo("First thought Second thought");
        assertThat(parsed.hasThinking()).isTrue();
    }

    @Test
    @DisplayName("concatenates multiple answer parts")
    void shouldConcatenateMultipleAnswerParts() {
        // Given: Event with multiple answer parts
        Part a1 = Part.builder().text("Hello ").build();
        Part a2 = Part.builder().text("World").build();
        Content content = Content.fromParts(a1, a2);
        Event event = Event.builder().content(content).build();

        // When: Parsing
        EventContentParser.ParsedContent parsed = EventContentParser.parse(event);

        // Then: answer should be concatenated
        assertThat(parsed.answerText()).isEqualTo("Hello World");
        assertThat(parsed.hasThinking()).isFalse();
    }

    @Test
    @DisplayName("stringifyContent still works as fallback")
    void stringifyContentShouldPreserveBackwardCompatibility() {
        // Given: Event with mixed parts
        Part thinkingPart = Part.builder().text("thinking...").thought(true).build();
        Part answerPart = Part.builder().text("answer.").build();
        Content content = Content.fromParts(thinkingPart, answerPart);
        Event event = Event.builder().content(content).build();

        // When: Using stringifyContent (legacy method)
        String allText = event.stringifyContent();

        // Then: it should still return all text concatenated
        assertThat(allText).contains("thinking...");
        assertThat(allText).contains("answer.");
    }
}
