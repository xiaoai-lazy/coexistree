package io.github.xiaoailazy.coexistree.agent.config;

import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.Callbacks;
import com.google.adk.agents.LlmAgent;
import com.google.adk.apps.App;
import com.google.adk.artifacts.InMemoryArtifactService;
import com.google.adk.models.langchain4j.LangChain4j;
import com.google.adk.runner.Runner;
import com.google.adk.tools.AgentTool;
import com.google.adk.tools.FunctionTool;
import com.google.adk.summarizer.EventsCompactionConfig;
import io.github.xiaoailazy.coexistree.agent.context.TreeContextLoader;
import io.github.xiaoailazy.coexistree.agent.observability.AgentObservationCallbacks;
import io.github.xiaoailazy.coexistree.agent.session.DatabaseSessionService;
import io.github.xiaoailazy.coexistree.agent.tools.ReadDocumentTool;
import io.github.xiaoailazy.coexistree.agent.tools.ReadNodeTextTool;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import io.github.xiaoailazy.coexistree.config.LlmProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Agent configuration for Google ADK.
 * Defines Root Agent as an orchestrator, qa-agent, and eval-agent as Spring Beans.
 *
 * Agent hierarchy:
 *   root-agent (orchestrator - intent routing + multi-turn dialog + agent dispatch)
 *   |-- qa-agent (search tree + answer with citations)
 *   |-- eval-agent (evaluate requirements)
 */
@Slf4j
@Configuration
public class AgentConfig {

    private final LlmProperties llmProperties;

    @Value("${app.adk.model:${ADK_MODEL:#{null}}}")
    private String adkModel;

    public AgentConfig(LlmProperties llmProperties) {
        this.llmProperties = llmProperties;
    }

    // ==================== LLM Model ====================

    @Bean
    public LangChain4j adkLlm() {
        String model = (adkModel != null && !adkModel.isBlank())
                ? adkModel
                : llmProperties.getModel();
        String baseUrl = llmProperties.getBaseUrl();
        String apiKey = llmProperties.getApiKey();

        log.info("Creating ADK LangChain4j LLM with model={}, baseUrl={}", model, baseUrl);

        int timeout = llmProperties.getTimeout();
        if (timeout <= 0) timeout = 60000; // 60 seconds default

        StreamingChatModel streamingModel = OpenAiStreamingChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(model)
                .timeout(java.time.Duration.ofMillis(timeout))
                .logRequests(true)
                .build();

        ChatModel chatModel = OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(model)
                .timeout(java.time.Duration.ofMillis(timeout))
                .logRequests(true)
                .build();

        return LangChain4j.builder()
                .chatModel(chatModel)
                .streamingChatModel(streamingModel)
                .modelName(model)
                .build();
    }

    @Bean
    public ReadNodeTextTool readNodeTextTool(
            io.github.xiaoailazy.coexistree.document.service.DocumentTreeService documentTreeService
    ) {
        return new ReadNodeTextTool(documentTreeService);
    }

    @Bean
    public ReadDocumentTool readDocumentTool(
            io.github.xiaoailazy.coexistree.document.repository.DocumentRepository documentRepository
    ) {
        return new ReadDocumentTool(documentRepository);
    }

    // ==================== Agents ====================

    /**
     * Compose TreeContextLoader + AgentObservationCallbacks into a single beforeModelCallback.
     * ADK only keeps the last registered callback per type, so we must compose them.
     */
    private Callbacks.BeforeModelCallback composedBeforeModelCallback(
            TreeContextLoader treeContextLoader,
            AgentObservationCallbacks obsCallbacks
    ) {
        Callbacks.BeforeModelCallback treeCb = treeContextLoader.createBeforeModelCallback();
        Callbacks.BeforeModelCallback obsCb = obsCallbacks.createBeforeModelCallback();
        return (ctx, builder) -> {
            treeCb.call(ctx, builder);
            return obsCb.call(ctx, builder);
        };
    }

    @Bean
    public LlmAgent qaAgent(
            LangChain4j adkLlm,
            ReadNodeTextTool readNodeTextTool,
            ReadDocumentTool readDocumentTool,
            TreeContextLoader treeContextLoader,
            AgentObservationCallbacks obsCallbacks
    ) {
        return LlmAgent.builder()
                .name("qa-agent")
                .description("Read document content from the knowledge tree and generate accurate answers with citations. The knowledge tree structure is automatically loaded as context.")
                .model(adkLlm)
                .instruction("""
                        You are a QA Assistant for the CoExistree knowledge management system.
                        A knowledge tree structure has been provided in the context above.

                        ## CRITICAL RULE: Always call readNodeTexts FIRST
                        You MUST call the readNodeTexts tool with relevant node IDs BEFORE generating any answer.
                        NEVER answer directly using only the tree structure summaries — they are not sufficient for a complete answer.

                        ## Workflow
                        1. Examine the knowledge tree structure to identify which nodes are relevant to the user's question
                        2. Call readNodeTexts with a JSON array: [{"docId": X, "nodeId": "..."}, ...]
                        3. Read the tool response which contains the full text of each node
                        4. Generate a detailed answer based ONLY on the actual node text
                        5. Cite sources: [来源: 节点标题]

                        ## If no relevant nodes exist
                        Say "信息不足，当前知识库中没有相关内容" — do NOT invent answers.

                        Always respond in the same language as the user's question.
                        """)
                .outputKey("last_qa_response")
                .tools(
                        FunctionTool.create(readNodeTextTool, "readNodeTexts"),
                        FunctionTool.create(readDocumentTool, "readDocument")
                )
                .beforeModelCallback(composedBeforeModelCallback(treeContextLoader, obsCallbacks))
                .afterModelCallback(obsCallbacks.createAfterModelCallback())
                .onModelErrorCallback(obsCallbacks.createOnModelErrorCallback())
                .beforeAgentCallback(obsCallbacks.createBeforeAgentCallback())
                .afterAgentCallback(obsCallbacks.createAfterAgentCallback())
                .beforeToolCallback(obsCallbacks.createBeforeToolCallback())
                .afterToolCallback(obsCallbacks.createAfterToolCallback())
                .onToolErrorCallback(obsCallbacks.createOnToolErrorCallback())
                .build();
    }

    @Bean
    public LlmAgent evalAgent(
            LangChain4j adkLlm,
            ReadDocumentTool readDocumentTool,
            AgentObservationCallbacks obsCallbacks
    ) {
        return LlmAgent.builder()
                .name("eval-agent")
                .description("Evaluate requirement documents against the existing knowledge tree for conflicts, consistency, and impact")
                .model(adkLlm)
                .instruction("""
                        You are an Evaluation Assistant for the CoExistree knowledge management system.
                        Read requirement documents and evaluate them against the existing knowledge tree.

                        Perform four checks:
                        1. Conflict detection — does this conflict with existing features?
                        2. Consistency check — is this consistent with system constraints?
                        3. Impact analysis — which existing modules will this affect?
                        4. History check — are there similar past requirements?

                        For each check, output risk_level (Low/Medium/High), description, and suggestion.
                        """)
                .tools(
                        FunctionTool.create(readDocumentTool, "readDocument")
                )
                .beforeAgentCallback(obsCallbacks.createBeforeAgentCallback())
                .afterAgentCallback(obsCallbacks.createAfterAgentCallback())
                .beforeModelCallback(obsCallbacks.createBeforeModelCallback())
                .afterModelCallback(obsCallbacks.createAfterModelCallback())
                .onModelErrorCallback(obsCallbacks.createOnModelErrorCallback())
                .beforeToolCallback(obsCallbacks.createBeforeToolCallback())
                .afterToolCallback(obsCallbacks.createAfterToolCallback())
                .onToolErrorCallback(obsCallbacks.createOnToolErrorCallback())
                .build();
    }

    @Bean
    public LlmAgent rootAgent(
            LangChain4j adkLlm,
            LlmAgent qaAgent,
            LlmAgent evalAgent,
            AgentObservationCallbacks obsCallbacks
    ) {
        String instruction = """
You are the Root Agent, an orchestrator for the CoExistree knowledge management system.
Your ONLY job is to analyze user input and delegate to the correct sub-agent.

## Available Tools
- `qa-agent`: Search knowledge tree and answer questions about system content. Call this for ANY question about what the system has, features, capabilities, modules, or content.
- `eval-agent`: Evaluate requirement documents against existing knowledge tree. Call this ONLY when the user explicitly mentions a document, requirement, or spec to evaluate.

## Rules

Rule 1: If the user asks a question about the system (features, content, capabilities, modules, functions), you MUST call the qa-agent tool. Do NOT answer directly.
  Example: User says "这个系统有几个功能？" -- you MUST call qa-agent.

Rule 2: If the user asks to evaluate, analyze, or review a requirement document, you MUST call the eval-agent tool.
  Example: User says "请评估这份需求文档" -- you MUST call eval-agent.

Rule 3: If the user says a simple greeting like "你好" or "hello", respond directly without any tool.

Rule 4: If the user asks how to use the system (meta-questions like "你能做什么"), respond directly without any tool.

Rule 5: If the user's intent is truly unclear (not a greeting, not a knowledge question, not an evaluation request), ask a clarifying question without any tool.

## Self-Check
Before responding, verify:
- If the input is a QUESTION about system features/content -- call qa-agent
- If the input is an EVALUATION request -- call eval-agent
- If the input is a GREETING -- respond directly
- If the input is UNCLEAR -- ask clarifying question

## FORBIDDEN
- NEVER answer knowledge questions using your own knowledge
- NEVER skip calling the appropriate tool when rules apply
- NEVER treat knowledge questions as unclear intent

Always respond in the same language as the user's input.
""";

        return LlmAgent.builder()
                .name("root-agent")
                .description("Orchestrator that analyzes user requests and delegates to specialized agents")
                .model(adkLlm)
                .instruction(instruction)
                .tools(
                        AgentTool.create(qaAgent),
                        AgentTool.create(evalAgent)
                )
                .beforeAgentCallback(obsCallbacks.createBeforeAgentCallback())
                .afterAgentCallback(obsCallbacks.createAfterAgentCallback())
                .beforeModelCallback(obsCallbacks.createBeforeModelCallback())
                .afterModelCallback(obsCallbacks.createAfterModelCallback())
                .onModelErrorCallback(obsCallbacks.createOnModelErrorCallback())
                .beforeToolCallback(obsCallbacks.createBeforeToolCallback())
                .afterToolCallback(obsCallbacks.createAfterToolCallback())
                .onToolErrorCallback(obsCallbacks.createOnToolErrorCallback())
                .build();
    }

    // ==================== ADK App & Runner ====================

    @Bean
    public App adkApp(BaseAgent rootAgent) {
        return App.builder()
                .name("coexistree")
                .rootAgent(rootAgent)
                .eventsCompactionConfig(EventsCompactionConfig.builder()
                        .compactionInterval(5)
                        .overlapSize(1)
                        .build())
                .build();
    }

    @Bean
    public Runner runner(App adkApp, DatabaseSessionService sessionService) {
        return Runner.builder()
                .app(adkApp)
                .sessionService(sessionService)
                .artifactService(new InMemoryArtifactService())
                .build();
    }
}
