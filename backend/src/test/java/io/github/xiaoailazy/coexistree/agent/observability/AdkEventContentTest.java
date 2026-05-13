package io.github.xiaoailazy.coexistree.agent.observability;

import com.google.adk.events.Event;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that ADK Event builder correctly populates content
 * when constructed with Content.fromParts().
 */
class AdkEventContentTest {

    @Test
    @DisplayName("Event.builder().content() should return the content set on builder")
    void eventShouldReturnContent() {
        // Given: Event with content
        Part chunk = Part.builder().text("Hello").build();
        Content content = Content.fromParts(chunk);
        Event event = Event.builder()
                .content(content)
                .partial(true)
                .author("qa-agent")
                .build();

        // Then: content() should be present
        assertThat(event.content()).isPresent();
        assertThat(event.content().get().parts()).isPresent();
        assertThat(event.content().get().parts().get()).hasSize(1);

        // And: EventContentParser should extract the text
        EventContentParser.ParsedContent parsed = EventContentParser.parse(event);
        assertThat(parsed.answerText()).isEqualTo("Hello");
        assertThat(parsed.hasThinking()).isFalse();
    }

    @Test
    @DisplayName("Event with thinking Part should be parsed correctly")
    void eventWithThinkingPartShouldBeParsed() {
        Part thinkingPart = Part.builder().text("Thinking...").thought(true).build();
        Content content = Content.fromParts(thinkingPart);
        Event event = Event.builder()
                .content(content)
                .partial(true)
                .build();

        EventContentParser.ParsedContent parsed = EventContentParser.parse(event);
        assertThat(parsed.thinkingText()).isEqualTo("Thinking...");
        assertThat(parsed.answerText()).isEmpty();
    }
}
