package io.github.xiaoailazy.coexistree.agent.service;

import io.github.xiaoailazy.coexistree.chat.dto.ChatRequest;
import io.github.xiaoailazy.coexistree.chat.dto.SseEvent;
import io.github.xiaoailazy.coexistree.chat.entity.ConversationEntity;
import io.github.xiaoailazy.coexistree.chat.repository.ConversationRepository;
import io.github.xiaoailazy.coexistree.security.model.SecurityUserDetails;
import io.github.xiaoailazy.coexistree.user.entity.UserEntity;
import io.github.xiaoailazy.coexistree.user.entity.UserRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
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
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for root-agent routing with real LLM calls via full HTTP pipeline.
 *
 * Verifies that root-agent correctly acts as an orchestrator:
 * - Routes knowledge questions to qa-agent (stage events)
 * - Routes document evaluation requests to eval-agent (stage events)
 * - Asks clarifying questions for unclear input (answer text, no stage events)
 * - Responds to greetings directly (answer text, no stage events)
 *
 * Uses MockMvc → HTTP → SseEmitter full pipeline, same as production.
 * Set LLM_TEST_ENABLED=true to enable these tests.
 */
@Slf4j
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RootAgentIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ConversationRepository conversationRepository;

    private String conversationId;

    private static SecurityUserDetails createTestUser() {
        UserEntity user = new UserEntity();
        user.setId(1L);
        user.setUsername("admin");
        user.setDisplayName("系统管理员");
        user.setPasswordHash("test-password");
        user.setRole(UserRole.USER);
        return new SecurityUserDetails(user);
    }

    @BeforeEach
    void setUp() {
        String enabled = System.getenv("LLM_TEST_ENABLED");
        if (enabled == null) {
            enabled = System.getProperty("LLM_TEST_ENABLED");
        }
        boolean shouldRun = "true".equalsIgnoreCase(enabled);
        org.slf4j.LoggerFactory.getLogger(getClass())
                .info("LLM_TEST_ENABLED={}, should run tests={}", enabled, shouldRun);
        Assumptions.assumeTrue(shouldRun, "Skipping LLM tests. Set LLM_TEST_ENABLED=true to enable.");

        // Create a conversation for this test run
        conversationId = "root-agent-test-" + java.util.UUID.randomUUID().toString().substring(0, 8);
        ConversationEntity conversation = new ConversationEntity();
        conversation.setConversationId(conversationId);
        conversation.setSystemId(2L);
        conversation.setTitle("Root Agent Test Conversation");
        conversation.setCreatedAt(LocalDateTime.now());
        conversation.setUpdatedAt(LocalDateTime.now());
        conversationRepository.save(conversation);
    }

    /**
     * Helper: send one smart-chat request and collect SSE events.
     */
    private AgentRunResult runChatRound(String question) throws Exception {
        List<String> stageEvents = new ArrayList<>();
        List<String> toolCallNames = new ArrayList<>();
        List<String> thinkingEvents = new ArrayList<>();
        List<String> answerEvents = new ArrayList<>();
        StringBuilder answerBuffer = new StringBuilder();
        boolean hasDoneEvent = false;

        MvcResult mvcResult = mockMvc.perform(
                        post("/api/v1/conversations/" + conversationId + "/smart-chat")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(ChatRequest.question(question)))
                                .with(csrf())
                                .with(user(createTestUser())))
                .andExpect(request().asyncStarted())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM_VALUE))
                .andReturn();

        MvcResult asyncResult = mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andReturn();

        String rawContent = asyncResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
        if (rawContent != null && !rawContent.isBlank()) {
            for (String line : rawContent.split("\n")) {
                String trimmed = line.trim();
                if (trimmed.startsWith("data:")) {
                    String json = trimmed.substring(5).trim();
                    try {
                        SseEvent event = objectMapper.readValue(json, SseEvent.class);
                        switch (event.type()) {
                            case "stage" -> {
                                if (event.content() != null) {
                                    stageEvents.add(event.content());
                                }
                            }
                            case "answer" -> {
                                if (event.content() != null) {
                                    answerEvents.add(event.content());
                                    answerBuffer.append(event.content());
                                }
                            }
                            case "thinking" -> {
                                if (event.content() != null) {
                                    thinkingEvents.add(event.content());
                                }
                            }
                            case "done" -> hasDoneEvent = true;
                            case "error" -> {
                                if (event.content() != null) {
                                    throw new RuntimeException("SSE error: " + event.content());
                                }
                            }
                        }
                    } catch (Exception e) {
                        // Some SSE lines may not be parseable — skip
                    }
                }
            }
        }

        AgentRunResult result = new AgentRunResult();
        result.answerBuffer.append(answerBuffer);
        result.hasSearchStage = !stageEvents.isEmpty();
        result.stageEvents.addAll(stageEvents);
        result.thinkingEvents.addAll(thinkingEvents);
        result.answerEvents.addAll(answerEvents);
        result.hasDoneEvent = hasDoneEvent;

        return result;
    }

    @Test
    @DisplayName("Should route knowledge questions to qa-agent")
    void shouldRouteToQaAgent() throws Exception {
        AgentRunResult result = runChatRound("这个系统有几个功能？");

        assertThat(result.hasSearchStage)
                .as("Should trigger search stage (qa-agent tool call) for knowledge questions. Stages: %s, Answer: %s",
                        result.stageEvents, truncate(result.answerBuffer.toString(), 300))
                .isTrue();
    }

    @Test
    @DisplayName("Should route document evaluation requests to eval-agent")
    void shouldRouteToEvalAgent() throws Exception {
        AgentRunResult result = runChatRound("请帮我评估这份需求文档，看看有没有和现有功能冲突的地方");

        assertThat(result.hasSearchStage)
                .as("Should trigger search stage (eval-agent tool call) for document evaluation. Stages: %s",
                        result.stageEvents)
                .isTrue();
    }

    @Test
    @DisplayName("Should ask clarifying question for unclear input")
    void shouldAskClarifyingQuestion() throws Exception {
        AgentRunResult result = runChatRound("我想做点什么");

        assertThat(result.hasSearchStage)
                .as("Should NOT trigger search stage for unclear input. Stages: %s", result.stageEvents)
                .isFalse();

        assertThat(result.answerBuffer.toString())
                .as("Should ask a clarifying question")
                .isNotBlank();
    }

    @Test
    @DisplayName("Should respond to greetings without tool calls")
    void shouldRespondToGreetingsWithoutToolCalls() throws Exception {
        AgentRunResult result = runChatRound("你好");

        assertThat(result.hasSearchStage)
                .as("Should NOT trigger search stage for simple greetings. Stages: %s", result.stageEvents)
                .isFalse();

        assertThat(result.answerBuffer.toString())
                .as("Should produce a direct response for greetings")
                .isNotBlank();
    }

    @Test
    @DisplayName("Should route various knowledge question phrasings to qa-agent")
    void shouldRouteVariousKnowledgePhrasingsToQaAgent() throws Exception {
        List<String> knowledgeQuestions = List.of(
                "这个系统有几个功能？",
                "系统里有什么内容？",
                "有哪些模块？"
        );

        for (String question : knowledgeQuestions) {
            AgentRunResult result = runChatRound(question);

            assertThat(result.hasSearchStage)
                    .as("Should trigger search stage (qa-agent) for: '%s'. Stages: %s", question, result.stageEvents)
                    .isTrue();
        }
    }

    @Test
    @DisplayName("Should route evaluation phrasing to eval-agent")
    void shouldRouteEvaluationPhrasingToEvalAgent() throws Exception {
        // Use the most unambiguous phrasing
        AgentRunResult result = runChatRound("请帮我评估这份需求文档，看看有没有和现有功能冲突的地方");

        assertThat(result.hasSearchStage)
                .as("Should trigger search stage (eval-agent) for document evaluation. Stages: %s", result.stageEvents)
                .isTrue();
    }

    @Test
    @DisplayName("Should emit thinking, answer, stage, and done events in SSE stream")
    void shouldEmitThinkingAnswerStageAndDoneEvents() throws Exception {
        AgentRunResult result = runChatRound("这个系统有几个功能？");

        assertThat(result.hasSearchStage)
                .as("Should trigger search stage (qa-agent tool call) for knowledge questions. Stages: %s",
                        result.stageEvents)
                .isTrue();

        assertThat(result.answerEvents)
                .as("Should have at least 2 answer events (proves incremental streaming). Answer events: %d",
                        result.answerEvents.size())
                .hasSizeGreaterThanOrEqualTo(2);

        assertThat(result.hasDoneEvent)
                .as("Should end with a done event")
                .isTrue();

        // Thinking events may or may not appear depending on model behavior
        // Log them for visibility but don't assert (some models don't stream thinking via SSE)
        log.info("Thinking events captured: {}", result.thinkingEvents.size());
        for (String thinking : result.thinkingEvents) {
            log.info("Thinking chunk: {}", thinking.substring(0, Math.min(100, thinking.length())));
        }
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "(empty)";
        return s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }

    /**
     * Captures the result of a smart-chat round.
     */
    private static class AgentRunResult {
        final StringBuilder answerBuffer = new StringBuilder();
        final List<String> stageEvents = new ArrayList<>();
        final List<String> thinkingEvents = new ArrayList<>();
        final List<String> answerEvents = new ArrayList<>();
        boolean hasSearchStage;
        boolean hasDoneEvent;
    }
}
