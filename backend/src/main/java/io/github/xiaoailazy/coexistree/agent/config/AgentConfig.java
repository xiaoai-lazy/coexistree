package io.github.xiaoailazy.coexistree.agent.config;

import com.google.adk.agents.LlmAgent;
import com.google.adk.tools.AgentTool;
import com.google.adk.tools.FunctionTool;
import io.github.xiaoailazy.coexistree.agent.llm.OpenAiLlm;
import io.github.xiaoailazy.coexistree.agent.tools.GetSecurityLevelTool;
import io.github.xiaoailazy.coexistree.agent.tools.ListSystemsTool;
import io.github.xiaoailazy.coexistree.agent.tools.ReadDocumentTool;
import io.github.xiaoailazy.coexistree.agent.tools.ReadNodeTextTool;
import io.github.xiaoailazy.coexistree.agent.tools.SearchTreeTool;
import io.github.xiaoailazy.coexistree.config.LlmProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Agent configuration for Google ADK.
 * Defines Root Agent, qa-agent, and eval-agent as Spring Beans.
 *
 * Agent hierarchy:
 *   root-agent (router)
 *   |-- qa-agent (search tree + answer with citations)
 *   |-- eval-agent (evaluate requirements)
 */
@Slf4j
@Configuration
public class AgentConfig {

    private final LlmProperties llmProperties;

    @Value("${app.adk.model:#{null}}")
    private String adkModel;

    public AgentConfig(LlmProperties llmProperties) {
        this.llmProperties = llmProperties;
    }

    // ==================== LLM Model ====================

    @Bean
    public OpenAiLlm adkLlm() {
        String model = (adkModel != null && !adkModel.isBlank())
                ? adkModel
                : llmProperties.getModel();
        log.info("Creating ADK OpenAiLlm with model={}", model);

        com.openai.client.OpenAIClient client = com.openai.client.okhttp.OpenAIOkHttpClient.builder()
                .apiKey(llmProperties.getApiKey())
                .baseUrl(llmProperties.getBaseUrl())
                .build();

        return new OpenAiLlm(model, client);
    }

    // ==================== Tools ====================

    @Bean
    public SearchTreeTool searchTreeTool(
            io.github.xiaoailazy.coexistree.knowledge.service.SystemKnowledgeTreeService treeService,
            io.github.xiaoailazy.coexistree.chat.service.TreeSearchService searchService
    ) {
        return new SearchTreeTool(treeService, searchService);
    }

    @Bean
    public ReadNodeTextTool readNodeTextTool(
            io.github.xiaoailazy.coexistree.document.service.DocumentTreeService documentTreeService
    ) {
        return new ReadNodeTextTool(documentTreeService);
    }

    @Bean
    public ReadDocumentTool readDocumentTool(
            io.github.xiaoailazy.coexistree.document.repository.DocumentRepository documentRepository,
            io.github.xiaoailazy.coexistree.document.storage.MarkdownFileStorageService storageService
    ) {
        return new ReadDocumentTool(documentRepository, storageService);
    }

    @Bean
    public GetSecurityLevelTool getSecurityLevelTool() {
        return new GetSecurityLevelTool();
    }

    @Bean
    public ListSystemsTool listSystemsTool(
            io.github.xiaoailazy.coexistree.system.repository.SystemUserMappingRepository mappingRepository
    ) {
        return new ListSystemsTool(mappingRepository);
    }

    // ==================== Agents ====================

    @Bean
    public LlmAgent qaAgent(
            OpenAiLlm adkLlm,
            SearchTreeTool searchTreeTool,
            ReadNodeTextTool readNodeTextTool,
            ReadDocumentTool readDocumentTool,
            GetSecurityLevelTool getSecurityLevelTool,
            ListSystemsTool listSystemsTool
    ) {
        return LlmAgent.builder()
                .name("qa-agent")
                .description("Search the knowledge tree, read document content, and generate accurate answers with citations")
                .model(adkLlm)
                .instruction("""
                        You are a QA Assistant for the CoExistree knowledge management system.
                        Search the knowledge tree, read relevant content, and generate accurate answers.

                        Rules:
                        1. Cite sources: [来源: 节点标题]
                        2. Be honest — say "信息不足" if you can't find enough content
                        3. Use the same language as the user's question

                        Workflow:
                        1. Use search_tree to find relevant nodes
                        2. Use read_node_text to read their content
                        3. Generate answers with citations
                        """)
                .tools(
                        FunctionTool.create(searchTreeTool, "execute"),
                        FunctionTool.create(readNodeTextTool, "execute"),
                        FunctionTool.create(readDocumentTool, "execute"),
                        FunctionTool.create(getSecurityLevelTool, "execute"),
                        FunctionTool.create(listSystemsTool, "execute")
                )
                .build();
    }

    @Bean
    public LlmAgent evalAgent(
            OpenAiLlm adkLlm,
            ReadDocumentTool readDocumentTool
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
                        FunctionTool.create(readDocumentTool, "execute")
                )
                .build();
    }

    @Bean
    public LlmAgent rootAgent(
            OpenAiLlm adkLlm,
            LlmAgent qaAgent,
            LlmAgent evalAgent
    ) {
        String instruction = """
                You are the Root Agent, an intelligent router for the CoExistree knowledge management system.

                ## Responsibilities
                Analyze the user's request and route it to the appropriate specialized agent:

                ### Available Sub-Agents
                1. **qa-agent** - For questions about the knowledge tree content.
                   Use when the user asks questions, searches for information, or needs answers
                   from the knowledge base. This agent can search the tree, read node content,
                   and generate cited answers.

                2. **eval-agent** - For evaluating requirement documents.
                   Use when the user wants to analyze a requirement document for conflicts,
                   consistency, impact, or historical patterns.

                ## Routing Rules
                - If the request is about searching/finding information in the knowledge tree -> qa-agent
                - If the request is about analyzing/evaluating a document -> eval-agent
                - If unclear, ask the user to clarify their intent

                Always respond in the same language as the user's input.
                """;

        return LlmAgent.builder()
                .name("root-agent")
                .description("Intelligent router that analyzes user requests and delegates to specialized agents")
                .model(adkLlm)
                .instruction(instruction)
                .tools(
                        AgentTool.create(qaAgent),
                        AgentTool.create(evalAgent)
                )
                .build();
    }
}
