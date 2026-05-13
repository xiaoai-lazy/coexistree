package io.github.xiaoailazy.coexistree.agent.session;

import com.google.adk.agents.LlmAgent;
import com.google.adk.apps.App;
import com.google.adk.artifacts.InMemoryArtifactService;
import com.google.adk.events.Event;
import com.google.adk.models.langchain4j.LangChain4j;
import com.google.adk.runner.Runner;
import com.google.adk.sessions.Session;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import io.github.xiaoailazy.coexistree.agent.observability.AgentObservationCallbacks;
import io.github.xiaoailazy.coexistree.config.LlmProperties;
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
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests whether manually pre-loading session state with temp:runId
 * makes it visible in ADK lifecycle callbacks.
 *
 * Premise: DatabaseSessionService.createSession() accepts a state parameter.
 * If we create the session before runAsync (with autoCreateSession=false),
 * the callbacks should see temp:runId from ctx.state() on the first invocation.
 *
 * Gated by LLM_TEST_ENABLED=true.
 */
@Slf4j
@SpringBootTest
@ActiveProfiles("test")
class SessionStatePropagationTest {

    @Autowired
    private DatabaseSessionService sessionService;

    @Autowired
    private AgentObservationCallbacks obsCallbacks;

    @Autowired
    private LlmProperties llmProperties;

    private LangChain4j llm;

    @BeforeEach
    void setUp() {
        String enabled = System.getenv("LLM_TEST_ENABLED");
        if (enabled == null) {
            enabled = System.getProperty("LLM_TEST_ENABLED");
        }
        Assumptions.assumeTrue("true".equalsIgnoreCase(enabled),
                "Skipping session state propagation test. Set LLM_TEST_ENABLED=true to enable.");

        String model = llmProperties.getModel();
        String baseUrl = llmProperties.getBaseUrl();
        String apiKey = llmProperties.getApiKey();

        StreamingChatModel streamingModel = OpenAiStreamingChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(model)
                .build();

        ChatModel chatModel = OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(model)
                .build();

        this.llm = LangChain4j.builder()
                .chatModel(chatModel)
                .streamingChatModel(streamingModel)
                .modelName(model)
                .build();
    }

    @Test
    @DisplayName("Manually created session with temp:runId should persist state")
    void manuallyCreatedSessionShouldPersistTempRunId() {
        // Step 1: Create session with temp:runId pre-loaded
        ConcurrentHashMap<String, Object> state = new ConcurrentHashMap<>();
        state.put("temp:runId", "run-manual-test-123");
        state.put("user:userId", 1L);
        state.put("user:conversationId", "conv-test-session-state");

        String sessionId = "sess-state-test-" + java.util.UUID.randomUUID().toString().substring(0, 8);

        Session session = sessionService.createSession("coexistree", "admin", state, sessionId).blockingGet();

        assertThat(session.state()).containsEntry("temp:runId", "run-manual-test-123");
        // JSON serialization converts Long to Integer, so compare as numbers
        assertThat(session.state().get("user:userId")).isEqualTo(1);
        assertThat(session.state()).containsEntry("user:conversationId", "conv-test-session-state");
        log.info("Step 1: Session created with state keys: {}", session.state().keySet());

        // Step 2: Re-load session and verify state persists
        Session reloaded = sessionService.getSession("coexistree", "admin", sessionId, java.util.Optional.empty()).blockingGet();

        assertThat(reloaded).isNotNull();
        assertThat(reloaded.state()).containsEntry("temp:runId", "run-manual-test-123");
        assertThat(reloaded.state().get("user:userId")).isEqualTo(1);
        log.info("Step 2: Session reloaded, state keys: {}", reloaded.state().keySet());
    }

    @Test
    @DisplayName("temp:runId pre-loaded in session should be visible in ADK beforeAgent callback")
    void tempRunIdShouldBeVisibleInBeforeAgentCallback() throws Exception {
        // Step 1: Create session with temp:runId pre-loaded
        ConcurrentHashMap<String, Object> state = new ConcurrentHashMap<>();
        String testRunId = "run-callback-test-" + java.util.UUID.randomUUID().toString().substring(0, 8);
        state.put("temp:runId", testRunId);
        state.put("user:userId", 1L);
        state.put("user:conversationId", "conv-callback-test");

        String sessionId = "sess-callback-test-" + java.util.UUID.randomUUID().toString().substring(0, 8);

        Session session = sessionService.createSession("coexistree", "admin", state, sessionId).blockingGet();
        log.info("Created session: {}, pre-loaded temp:runId={}", sessionId, testRunId);

        // Step 2: Create a test agent that captures callback state
        AtomicReference<String> capturedRunId = new AtomicReference<>();
        AtomicReference<String> capturedConversationId = new AtomicReference<>();
        AtomicReference<String> capturedAgentName = new AtomicReference<>();

        LlmAgent testAgent = LlmAgent.builder()
                .name("test-state-agent")
                .description("Test agent for verifying state propagation")
                .model(llm)
                .instruction("You are a test assistant. Say hello.")
                .tools(List.of())
                .beforeAgentCallback(ctx -> {
                    String runId = (String) ctx.state().get("temp:runId");
                    String convId = (String) ctx.state().get("user:conversationId");
                    capturedRunId.set(runId);
                    capturedConversationId.set(convId);
                    capturedAgentName.set(ctx.agentName());
                    log.info("beforeAgent callback: runId={}, conversationId={}, agent={}", runId, convId, ctx.agentName());
                    return io.reactivex.rxjava3.core.Maybe.empty();
                })
                .beforeModelCallback((ctx, req) -> {
                    String runId = (String) ctx.state().get("temp:runId");
                    log.info("beforeModel callback: runId={}", runId);
                    if (capturedRunId.get() == null) {
                        capturedRunId.set(runId);
                    }
                    return io.reactivex.rxjava3.core.Maybe.empty();
                })
                .build();

        // Step 3: Create ADK app and runner
        App app = App.builder()
                .name("coexistree")
                .rootAgent(testAgent)
                .build();

        Runner runner = Runner.builder()
                .app(app)
                .sessionService(sessionService)
                .artifactService(new InMemoryArtifactService())
                .build();

        // Step 4: Run with autoCreateSession=false (session already exists)
        com.google.adk.agents.RunConfig runConfig = com.google.adk.agents.RunConfig.builder()
                .autoCreateSession(false)
                .build();

        Content userContent = Content.fromParts(Part.fromText("你好"));

        log.info("Starting runAsync with autoCreateSession=false, sessionId={}", sessionId);

        io.reactivex.rxjava3.core.Flowable<Event> events = runner.runAsync(
                "admin", sessionId, userContent, runConfig);

        TestSubscriber<Event> subscriber = events.test();
        subscriber.awaitDone(30, TimeUnit.SECONDS);
        subscriber.assertComplete().assertNoErrors();

        List<Event> eventList = subscriber.values();
        log.info("Received {} events from agent execution", eventList.size());

        // Step 5: Verify the callback saw temp:runId
        assertThat(capturedRunId.get())
                .as("beforeAgent callback should see temp:runId from pre-loaded session state")
                .isEqualTo(testRunId);

        assertThat(capturedConversationId.get())
                .as("beforeAgent callback should see user:conversationId from pre-loaded session state")
                .isEqualTo("conv-callback-test");

        assertThat(capturedAgentName.get())
                .as("beforeAgent callback should have agent name")
                .isEqualTo("test-state-agent");

        log.info("VERIFIED: temp:runId='{}' was visible in beforeAgent callback", capturedRunId.get());
    }
}
