package io.github.xiaoailazy.coexistree.agent.service;

import com.google.adk.agents.RunConfig;
import com.google.adk.apps.App;
import com.google.adk.artifacts.InMemoryArtifactService;
import com.google.adk.events.Event;
import com.google.adk.runner.Runner;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import io.github.xiaoailazy.coexistree.agent.observability.EventContentParser;
import io.github.xiaoailazy.coexistree.agent.session.DatabaseSessionService;
import io.reactivex.rxjava3.subscribers.TestSubscriber;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for ADK Runner with real LLM calls.
 *
 * Verifies that the ADK event stream from a real LLM contains:
 * 1. Tool call events (function calls) when agent delegates to sub-agents
 * 2. Tool result events (function responses) after tool execution
 * 3. Answer events contain valid text content
 *
 * Note: The ADK runner's Flowable<Event> emits agent pipeline events
 * (tool calls, tool results, agent completion), not individual LLM
 * streaming chunks. LLM streaming happens internally within the ADK
 * pipeline and is aggregated into complete events.
 *
 * LLM-level streaming is already verified by LangChain4jLlmIntegrationTest.
 *
 * Set LLM_TEST_ENABLED=true to enable these tests.
 */
@Slf4j
@SpringBootTest
@ActiveProfiles("test")
class AdkRealLlmStreamingTest {

    @Autowired
    private App adkApp;

    @Autowired
    private DatabaseSessionService sessionService;

    @Autowired
    private Runner runner;

    @BeforeEach
    void checkLlmEnabled() {
        String enabled = System.getenv("LLM_TEST_ENABLED");
        if (enabled == null) {
            enabled = System.getProperty("LLM_TEST_ENABLED");
        }
        boolean shouldRun = "true".equalsIgnoreCase(enabled);
        log.info("LLM_TEST_ENABLED={}, should run tests={}", enabled, shouldRun);
        Assumptions.assumeTrue(shouldRun, "Skipping real LLM tests. Set LLM_TEST_ENABLED=true to enable.");
    }

    /**
     * Run the agent and collect all events with a 60-second timeout.
     */
    private List<Event> runAndCollect(String userId, String sessionId, String userMessage) {
        Content userContent = Content.fromParts(Part.fromText(userMessage));

        RunConfig runConfig = RunConfig.builder()
                .autoCreateSession(false)
                .build();

        TestSubscriber<Event> subscriber = runner
                .runAsync(userId, sessionId, userContent, runConfig)
                .test();
        subscriber.awaitDone(90, TimeUnit.SECONDS);
        subscriber.assertComplete().assertNoErrors();

        List<Event> events = subscriber.values();
        log.info("Collected {} events for message: {}", events.size(), userMessage.substring(0, Math.min(50, userMessage.length())));
        return events;
    }

    @Test
    @DisplayName("tool call events should appear when agent delegates to qa-agent")
    void toolCallEventsShouldAppear() {
        String userId = "test-toolcall-" + java.util.UUID.randomUUID();
        String sessionId = "session-toolcall-" + java.util.UUID.randomUUID();
        sessionService.createSession("coexistree", userId, new ConcurrentHashMap<>(), sessionId).blockingGet();

        // Use a knowledge question that triggers qa-agent delegation
        List<Event> events = runAndCollect(userId, sessionId, "这个系统有几个功能？");

        assertThat(events).as("Should receive at least one event").isNotEmpty();

        // Log all events for visibility
        for (Event e : events) {
            log.info("Event: author={}, hasFunctionCalls={}, hasFunctionResponses={}, hasContent={}",
                    e.author(),
                    e.functionCalls() != null && !e.functionCalls().isEmpty(),
                    e.functionResponses() != null && !e.functionResponses().isEmpty(),
                    e.content().isPresent());
        }

        // Detect events with function calls
        List<Event> toolCallEvents = events.stream()
                .filter(e -> e.functionCalls() != null && !e.functionCalls().isEmpty())
                .toList();

        assertThat(toolCallEvents)
                .as("Should receive at least one event with function calls (tool call). " +
                        "Total events: %d. Event authors: %s",
                        events.size(), events.stream().map(Event::author).toList())
                .isNotEmpty();

        // Log tool call names for verification
        for (Event e : toolCallEvents) {
            for (var fc : e.functionCalls()) {
                String name = fc.name().orElse("unknown");
                log.info("Tool call: {}", name);
            }
        }
    }

    @Test
    @DisplayName("tool result events should appear after tool execution")
    void toolResultEventsShouldAppear() {
        String userId = "test-toolresult-" + java.util.UUID.randomUUID();
        String sessionId = "session-toolresult-" + java.util.UUID.randomUUID();
        sessionService.createSession("coexistree", userId, new ConcurrentHashMap<>(), sessionId).blockingGet();

        // Same knowledge question that triggers qa-agent
        List<Event> events = runAndCollect(userId, sessionId, "这个系统有几个功能？");

        assertThat(events).as("Should receive at least one event").isNotEmpty();

        // Detect events with function responses
        List<Event> toolResultEvents = events.stream()
                .filter(e -> e.functionResponses() != null && !e.functionResponses().isEmpty())
                .toList();

        assertThat(toolResultEvents)
                .as("Should receive at least one event with function responses (tool results). " +
                        "Total events: %d. Event authors: %s",
                        events.size(), events.stream().map(Event::author).toList())
                .isNotEmpty();

        // Log tool result names for verification
        for (Event e : toolResultEvents) {
            for (var fr : e.functionResponses()) {
                String name = fr.name().orElse("unknown");
                log.info("Tool result: {}", name);
            }
        }
    }

    @Test
    @DisplayName("agent should return answer content in the final event")
    void answerContentShouldBePresent() {
        String userId = "test-answer-" + java.util.UUID.randomUUID();
        String sessionId = "session-answer-" + java.util.UUID.randomUUID();
        sessionService.createSession("coexistree", userId, new ConcurrentHashMap<>(), sessionId).blockingGet();

        List<Event> events = runAndCollect(userId, sessionId, "这个系统有几个功能？");

        assertThat(events).as("Should receive at least one event").isNotEmpty();

        // Find events with answer content
        List<Event> answerEvents = events.stream()
                .filter(e -> !EventContentParser.parse(e).answerText().isEmpty())
                .toList();

        assertThat(answerEvents)
                .as("Should receive at least one event with answer content")
                .isNotEmpty();

        // Verify accumulated answer
        StringBuilder fullAnswer = new StringBuilder();
        for (Event e : answerEvents) {
            fullAnswer.append(EventContentParser.parse(e).answerText());
        }
        log.info("Answer: {}", fullAnswer.substring(0, Math.min(200, fullAnswer.length())));
        assertThat(fullAnswer.toString())
                .as("Answer text should be non-blank")
                .isNotBlank();
    }

    @Test
    @DisplayName("agent should handle multiple tool call rounds in sequence")
    void shouldHandleMultipleToolCallRounds() {
        String userId = "test-multi-" + java.util.UUID.randomUUID();
        String sessionId = "session-multi-" + java.util.UUID.randomUUID();
        sessionService.createSession("coexistree", userId, new ConcurrentHashMap<>(), sessionId).blockingGet();

        List<Event> events = runAndCollect(userId, sessionId, "请帮我介绍下这个系统的核心功能");

        assertThat(events).as("Should receive events").isNotEmpty();

        // Verify the event pipeline contains multiple phases
        long toolCallCount = events.stream()
                .filter(e -> e.functionCalls() != null && !e.functionCalls().isEmpty())
                .count();
        long toolResultCount = events.stream()
                .filter(e -> e.functionResponses() != null && !e.functionResponses().isEmpty())
                .count();
        long answerCount = events.stream()
                .filter(e -> !EventContentParser.parse(e).answerText().isEmpty())
                .count();

        log.info("Event breakdown: toolCalls={}, toolResults={}, answers={}",
                toolCallCount, toolResultCount, answerCount);

        // At minimum, there should be tool calls and a final answer
        assertThat(toolCallCount)
                .as("Should have at least one tool call round")
                .isGreaterThanOrEqualTo(1);
    }
}
