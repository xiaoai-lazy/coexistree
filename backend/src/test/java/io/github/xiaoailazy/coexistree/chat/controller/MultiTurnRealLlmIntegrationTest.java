package io.github.xiaoailazy.coexistree.chat.controller;

import io.github.xiaoailazy.coexistree.chat.dto.SseEvent;
import io.github.xiaoailazy.coexistree.chat.entity.ConversationEntity;
import io.github.xiaoailazy.coexistree.chat.repository.ConversationRepository;
import io.github.xiaoailazy.coexistree.observability.entity.ConversationRunEntity;
import io.github.xiaoailazy.coexistree.observability.entity.ObservationSpanEntity;
import io.github.xiaoailazy.coexistree.observability.entity.SpanEventEntity;
import io.github.xiaoailazy.coexistree.observability.repository.ConversationRunRepository;
import io.github.xiaoailazy.coexistree.observability.repository.ObservationSpanRepository;
import io.github.xiaoailazy.coexistree.observability.repository.SpanEventRepository;
import io.github.xiaoailazy.coexistree.security.model.SecurityUserDetails;
import io.github.xiaoailazy.coexistree.user.entity.UserEntity;
import io.github.xiaoailazy.coexistree.user.entity.UserRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import com.google.adk.models.langchain4j.LangChain4j;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.subscribers.TestSubscriber;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test for multi-turn conversation with real LLM calls.
 *
 * Verifies:
 * 1. Multi-turn context carrying (2nd round depends on 1st round's answer)
 * 2. Observability data recording (runs, spans, events)
 * 3. SSE event stream completeness
 *
 * Gated by LLM_TEST_ENABLED=true environment variable.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MultiTurnRealLlmIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ConversationRunRepository conversationRunRepository;

    @Autowired
    private ObservationSpanRepository observationSpanRepository;

    @Autowired
    private SpanEventRepository spanEventRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LangChain4j llm;

    @Autowired
    private ConversationRepository conversationRepository;

    private String conversationId;
    private Long systemId;

    private static SecurityUserDetails createTestUser() {
        UserEntity user = new UserEntity();
        user.setId(1L);
        user.setUsername("admin");
        user.setDisplayName("系统管理员");
        user.setPasswordHash("test-password");
        user.setRole(UserRole.SUPER_ADMIN);
        return new SecurityUserDetails(user);
    }

    @BeforeEach
    void setUp() {
        String enabled = System.getenv("LLM_TEST_ENABLED");
        if (enabled == null) {
            enabled = System.getProperty("LLM_TEST_ENABLED");
        }
        Assumptions.assumeTrue("true".equalsIgnoreCase(enabled),
                "Skipping real LLM tests. Set LLM_TEST_ENABLED=true to enable.");

        // Use existing system "cet" (id=2) owned by admin
        systemId = 2L;

        conversationId = "multi-turn-test-" + java.util.UUID.randomUUID().toString().substring(0, 8);
        ConversationEntity conversation = new ConversationEntity();
        conversation.setConversationId(conversationId);
        conversation.setSystemId(systemId);
        conversation.setTitle("Multi-Turn Test Conversation");
        conversation.setCreatedAt(LocalDateTime.now());
        conversation.setUpdatedAt(LocalDateTime.now());
        conversationRepository.save(conversation);
    }

    /**
     * Helper: Execute a smart-chat round and collect all SSE events.
     *
     * Returns the concatenated answer content from all "answer" type events.
     */
    private String runChatRound(String conversationId, String question, String roundLabel, Long systemId) throws Exception {
        List<String> eventTypes = new ArrayList<>();
        StringBuilder answerBuffer = new StringBuilder();

        String requestBody = objectMapper.writeValueAsString(
                new io.github.xiaoailazy.coexistree.chat.dto.ChatRequest(question, null, systemId));
        System.out.println("[" + roundLabel + "] Request body: " + requestBody);

        MvcResult mvcResult = mockMvc.perform(
                        post("/api/v1/conversations/" + conversationId + "/smart-chat")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                                .with(csrf())
                                .with(user(createTestUser())))
                .andExpect(request().asyncStarted())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM_VALUE))
                .andReturn();

        MvcResult asyncResult = mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andReturn();

        // Parse SSE events from response content
        String rawContent = asyncResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
        System.out.println("[" + roundLabel + "] Raw SSE content length: " + (rawContent != null ? rawContent.length() : 0));
        if (rawContent != null && !rawContent.isBlank()) {
            String[] lines = rawContent.split("\n");
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.startsWith("data:")) {
                    String json = trimmed.substring(5).trim();
                    System.out.println("[" + roundLabel + "] Raw SSE data: " + json);
                    try {
                        SseEvent event = objectMapper.readValue(json, SseEvent.class);
                        eventTypes.add(event.type());
                        if ("answer".equals(event.type()) && event.content() != null) {
                            answerBuffer.append(event.content());
                        }
                        if ("error".equals(event.type())) {
                            System.err.println("[" + roundLabel + "] ERROR event: " + event.content());
                        }
                    } catch (Exception e) {
                        // Some SSE data lines may not be parseable as SseEvent — skip
                    }
                }
            }
        }

        System.out.println("[" + roundLabel + "] Events received: " + eventTypes);
        System.out.println("[" + roundLabel + "] Answer length: " + answerBuffer.length());

        return answerBuffer.toString();
    }

    /**
     * Evaluate multi-turn conversation quality via a direct LLM call.
     *
     * Sends both rounds' questions and answers to the LLM and asks it to
     * judge whether the answers are reasonable and contextually connected.
     * Returns "PASS" or "FAIL".
     */
    private String evaluateAnswers(String q1, String a1, String q2, String a2) throws Exception {
        String answer1Preview = truncate(a1, 500);
        String answer2Preview = truncate(a2, 500);

        String prompt = """
                You are evaluating a multi-turn conversation test. Judge whether the answers are reasonable.

                Round 1 Question:
                ---BEGIN---
                %s
                ---END---

                Round 1 Answer:
                ---BEGIN---
                %s
                ---END---

                Round 2 Question:
                ---BEGIN---
                %s
                ---END---

                Round 2 Answer:
                ---BEGIN---
                %s
                ---END---

                Check:
                1. Is Round 1 answer relevant to the question?
                2. Is Round 2 answer contextually connected to Round 1 (e.g., refers to "第二个" / second feature)?

                Respond with exactly one word: PASS or FAIL
                """.formatted(q1, answer1Preview, q2, answer2Preview);

        LlmRequest request = LlmRequest.builder()
                .contents(List.of(Content.fromParts(Part.fromText(prompt))))
                .build();

        TestSubscriber<LlmResponse> subscriber = llm.generateContent(request, false).test();
        subscriber.awaitDone(10, TimeUnit.SECONDS);
        subscriber.assertComplete().assertNoErrors();

        List<LlmResponse> responses = subscriber.values();
        assertThat(responses).as("LLM evaluation should return at least one response").isNotEmpty();

        String text = responses.stream()
                .map(r -> r.content().map(Content::text).orElse(""))
                .reduce("", String::concat);

        System.out.println("[AI Evaluation] LLM response: " + text.trim());

        // Extract PASS or FAIL from the response
        String trimmed = text.trim();
        String firstWord = trimmed.split("\\s+")[0].toUpperCase();
        if (firstWord.equals("PASS")) return "PASS";
        if (firstWord.equals("FAIL")) return "FAIL";
        return "UNKNOWN:" + text;
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "(empty)";
        return s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }

    @Test
    @DisplayName("Multi-turn conversation: context carries, observability records created")
    void multiTurnConversationShouldCarryContextAndRecordObservability() throws Exception {
        // === Round 1: Initial question ===
        String answer1 = runChatRound(conversationId, "这个系统有几个功能？", "Round 1", systemId);

        assertThat(answer1)
                .as("Round 1 should produce a non-empty answer")
                .isNotBlank();

        // === Round 2: Follow-up question (requires round 1 context) ===
        String answer2 = runChatRound(conversationId, "那第二个功能是什么？", "Round 2", systemId);

        assertThat(answer2)
                .as("Round 2 should produce a non-empty answer (context carrying from round 1)")
                .isNotBlank();

        // Write answers to file for inspection
        java.nio.file.Files.writeString(
                java.nio.file.Path.of("C:\\Git\\CoExistree\\backend\\target\\test-answers.txt"),
                "Q1: 这个系统有几个功能？\nA1: " + answer1 + "\n\nQ2: 那第二个功能是什么？\nA2: " + answer2 + "\n",
                java.nio.charset.StandardCharsets.UTF_8);

        // === AI evaluation: judge answer quality and context carrying ===
        String evaluationResult = evaluateAnswers(
                "这个系统有几个功能？", answer1,
                "那第二个功能是什么？", answer2);

        assertThat(evaluationResult)
                .as("AI evaluation should judge answers as reasonable (PASS). " +
                    "Round 1: %s... | Round 2: %s...",
                    truncate(answer1, 50), truncate(answer2, 50))
                .isEqualTo("PASS");

        // === Observability verification ===

        // 1. conversation_runs: expect 2 records
        List<ConversationRunEntity> runs = conversationRunRepository
                .findByConversationIdAndStatusOrderByStartedAtAsc(conversationId, "success");
        assertThat(runs)
                .as("Should have 2 conversation runs (one per round)")
                .hasSize(2);

        for (ConversationRunEntity run : runs) {
            assertThat(run.getStatus())
                    .as("Run should be success")
                    .isEqualTo("success");
            assertThat(run.getDurationMs())
                    .as("Run should have non-null duration")
                    .isNotNull()
                    .isPositive();
            assertThat(run.getFinalAnswer())
                    .as("Run should have a non-empty final answer")
                    .isNotBlank();
        }

        // 2. observation_spans: expect spans for both runs
        List<ObservationSpanEntity> spans = observationSpanRepository
                .findByConversationIdOrderByStartedAtAsc(conversationId);
        assertThat(spans)
                .as("Should have observation spans for both rounds")
                .isNotEmpty();

        // Verify root-agent spans exist
        List<String> agentNames = spans.stream()
                .map(ObservationSpanEntity::getAgentName)
                .filter(n -> n != null)
                .toList();
        assertThat(agentNames)
                .as("Should have root-agent spans")
                .anyMatch(name -> name.contains("root-agent"));

        // Verify spans belong to 2 distinct runs
        List<String> distinctRunIds = observationSpanRepository
                .findDistinctRunIdsByConversationId(conversationId);
        assertThat(distinctRunIds)
                .as("Spans should belong to 2 distinct runs")
                .hasSize(2);

        // 3. span_events: expect events for both rounds
        List<SpanEventEntity> events = spanEventRepository
                .findByConversationIdOrderByOccurredAtAsc(conversationId);
        assertThat(events)
                .as("Should have span events for both rounds")
                .isNotEmpty();

        int eventCount = spanEventRepository.countByConversationId(conversationId);
        assertThat(eventCount)
                .as("countByConversationId should match queried event count")
                .isEqualTo(events.size());

        System.out.println("=== Multi-turn test summary ===");
        System.out.println("ConversationId: " + conversationId);
        System.out.println("Runs: " + runs.size());
        System.out.println("Spans: " + spans.size());
        System.out.println("Events: " + events.size());
        System.out.println("Round 1 answer (preview): " + answer1.substring(0, Math.min(200, answer1.length())));
        System.out.println("Round 2 answer (preview): " + answer2.substring(0, Math.min(200, answer2.length())));
    }
}
