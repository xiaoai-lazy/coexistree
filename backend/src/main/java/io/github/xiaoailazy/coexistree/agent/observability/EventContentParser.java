package io.github.xiaoailazy.coexistree.agent.observability;

import com.google.adk.events.Event;
import com.google.genai.types.Part;

import java.util.List;

/**
 * Extracts thinking and answer text from ADK Events.
 * ADK Event.content().parts() contains Part objects where Part.thought()
 * returns Optional<Boolean> — true means thinking content.
 */
public final class EventContentParser {

    private EventContentParser() {}

    public record ParsedContent(String thinkingText, String answerText) {
        public boolean hasThinking() {
            return !thinkingText.isEmpty();
        }
    }

    public static ParsedContent parse(Event event) {
        StringBuilder thinking = new StringBuilder();
        StringBuilder answer = new StringBuilder();

        event.content().ifPresent(content -> {
            content.parts().ifPresent(parts -> {
                for (Part part : parts) {
                    if (part == null) continue;
                    part.text().ifPresent(text -> {
                        boolean isThinking = part.thought().orElse(false);
                        if (isThinking) {
                            thinking.append(text);
                        } else {
                            answer.append(text);
                        }
                    });
                }
            });
        });

        return new ParsedContent(thinking.toString(), answer.toString());
    }
}
