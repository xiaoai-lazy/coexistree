package io.github.xiaoailazy.coexistree.chat.service;

import com.fasterxml.jackson.core.type.TypeReference;
import io.github.xiaoailazy.coexistree.shared.enums.ErrorCode;
import io.github.xiaoailazy.coexistree.shared.exception.BusinessException;
import io.github.xiaoailazy.coexistree.shared.util.JsonUtils;
import io.github.xiaoailazy.coexistree.chat.dto.ChatRequest;
import io.github.xiaoailazy.coexistree.chat.dto.ConversationResponse;
import io.github.xiaoailazy.coexistree.chat.dto.EvaluationSseEvent;
import io.github.xiaoailazy.coexistree.chat.dto.MessageResponse;
import io.github.xiaoailazy.coexistree.chat.dto.SseEvent;
import io.github.xiaoailazy.coexistree.chat.entity.ConversationEntity;
import io.github.xiaoailazy.coexistree.chat.entity.MessageEntity;
import io.github.xiaoailazy.coexistree.chat.repository.ConversationRepository;
import io.github.xiaoailazy.coexistree.chat.repository.MessageRepository;
import io.github.xiaoailazy.coexistree.document.entity.DocumentEntity;
import io.github.xiaoailazy.coexistree.document.repository.DocumentRepository;
import io.github.xiaoailazy.coexistree.document.service.DocumentTreeService;
import io.github.xiaoailazy.coexistree.document.storage.MarkdownFileStorageService;
import io.github.xiaoailazy.coexistree.review.enums.ConfidenceLevel;
import io.github.xiaoailazy.coexistree.review.enums.IntentType;
import io.github.xiaoailazy.coexistree.review.model.ClarificationOption;
import io.github.xiaoailazy.coexistree.review.model.EvaluationReport;
import io.github.xiaoailazy.coexistree.review.model.IntentResult;
import io.github.xiaoailazy.coexistree.review.service.IntentClassifier;
import io.github.xiaoailazy.coexistree.review.service.RequirementEvaluationService;
import io.github.xiaoailazy.coexistree.review.service.RequirementEvaluationService.EvaluationResult;
import io.github.xiaoailazy.coexistree.knowledge.model.SystemKnowledgeTree;
import io.github.xiaoailazy.coexistree.knowledge.service.SystemKnowledgeTreeService;
import io.github.xiaoailazy.coexistree.indexer.llm.LlmClient;
import io.github.xiaoailazy.coexistree.indexer.llm.PromptTemplateService;
import io.github.xiaoailazy.coexistree.indexer.model.Citation;
import io.github.xiaoailazy.coexistree.indexer.model.NodeSource;
import io.github.xiaoailazy.coexistree.indexer.model.TreeNode;
import io.github.xiaoailazy.coexistree.indexer.model.TreeSearchResult;
import io.github.xiaoailazy.coexistree.indexer.tree.TreeNodeMapper;
import io.github.xiaoailazy.coexistree.security.model.SecurityUserDetails;
import io.github.xiaoailazy.coexistree.system.entity.SystemUserMappingEntity;
import io.github.xiaoailazy.coexistree.system.repository.SystemUserMappingRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class ConversationServiceImpl implements ConversationService {

    private static final String FALLBACK_ANSWER = "无法依据文档内容回答该问题。";
    private static final String ROLE_USER = "USER";
    private static final String ROLE_ASSISTANT = "ASSISTANT";

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final SystemKnowledgeTreeService systemKnowledgeTreeService;
    private final DocumentTreeService documentTreeService;
    private final TreeNodeMapper treeNodeMapper;
    private final TreeSearchService treeSearchService;
    private final AnswerGenerationService answerGenerationService;
    private final PromptTemplateService promptTemplateService;
    private final LlmClient llmClient;
    private final JsonUtils jsonUtils;

    // 需求评估相关服务
    private final IntentClassifier intentClassifier;
    private final RequirementEvaluationService requirementEvaluationService;
    private final DocumentRepository documentRepository;
    private final MarkdownFileStorageService markdownFileStorageService;
    private final SystemUserMappingRepository systemUserMappingRepository;

    public ConversationServiceImpl(
            ConversationRepository conversationRepository,
            MessageRepository messageRepository,
            SystemKnowledgeTreeService systemKnowledgeTreeService,
            DocumentTreeService documentTreeService,
            TreeNodeMapper treeNodeMapper,
            TreeSearchService treeSearchService,
            AnswerGenerationService answerGenerationService,
            PromptTemplateService promptTemplateService,
            LlmClient llmClient,
            JsonUtils jsonUtils,
            IntentClassifier intentClassifier,
            RequirementEvaluationService requirementEvaluationService,
            DocumentRepository documentRepository,
            MarkdownFileStorageService markdownFileStorageService,
            SystemUserMappingRepository systemUserMappingRepository
    ) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.systemKnowledgeTreeService = systemKnowledgeTreeService;
        this.documentTreeService = documentTreeService;
        this.treeNodeMapper = treeNodeMapper;
        this.treeSearchService = treeSearchService;
        this.answerGenerationService = answerGenerationService;
        this.promptTemplateService = promptTemplateService;
        this.llmClient = llmClient;
        this.jsonUtils = jsonUtils;
        this.intentClassifier = intentClassifier;
        this.requirementEvaluationService = requirementEvaluationService;
        this.documentRepository = documentRepository;
        this.markdownFileStorageService = markdownFileStorageService;
        this.systemUserMappingRepository = systemUserMappingRepository;
    }

    @Override
    public ConversationResponse createConversation(Long systemId, String title) {
        ConversationEntity entity = new ConversationEntity();
        entity.setConversationId(UUID.randomUUID().toString());
        entity.setSystemId(systemId);
        entity.setTitle(title);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        conversationRepository.save(entity);
        log.info("创建会话成功, conversationId={}, systemId={}", entity.getConversationId(), systemId);
        return toResponse(entity);
    }

    @Override
    public List<ConversationResponse> listConversations() {
        return conversationRepository.findAllByOrderByUpdatedAtDesc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public ConversationResponse getConversation(String conversationId) {
        return toResponse(findConversation(conversationId));
    }

    @Override
    public List<MessageResponse> getMessages(String conversationId) {
        findConversation(conversationId);
        return messageRepository.findByConversationIdOrderByCreatedAt(conversationId)
                .stream()
                .map(m -> new MessageResponse(
                        m.getId(),
                        m.getRole(),
                        m.getContent(),
                        m.getThinking(),
                        parseCitations(m.getCitations()),
                        m.getCreatedAt()))
                .toList();
    }

    @Override
    @Transactional
    public void deleteConversation(String conversationId) {
        ConversationEntity entity = findConversation(conversationId);
        messageRepository.deleteByConversationId(conversationId);
        conversationRepository.delete(entity);
        log.info("删除会话成功, conversationId={}", conversationId);
    }


    private ConversationEntity findConversation(String conversationId) {
        return conversationRepository.findByConversationId(conversationId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.CONVERSATION_NOT_FOUND, "Conversation not found: " + conversationId));
    }

    private ConversationResponse toResponse(ConversationEntity entity) {
        return new ConversationResponse(
                entity.getConversationId(),
                entity.getSystemId(),
                entity.getTitle(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private void saveUserMessage(String conversationId, String question) {
        saveUserMessageWithDocument(conversationId, question, null);
    }

    private void saveAssistantMessage(String conversationId, String answer, String llmResponseId,
                                      String thinking, List<Citation> citations) {
        MessageEntity msg = new MessageEntity();
        msg.setConversationId(conversationId);
        msg.setRole(ROLE_ASSISTANT);
        msg.setContent(answer);
        msg.setLlmResponseId(llmResponseId);
        msg.setThinking(thinking);
        if (!citations.isEmpty()) {
            List<SseEvent.CitationDto> dtos = citations.stream().map(SseEvent.CitationDto::from).toList();
            msg.setCitations(jsonUtils.toJson(dtos));
        }
        msg.setCreatedAt(LocalDateTime.now());
        messageRepository.save(msg);
    }

    private void updateConversation(ConversationEntity conversation, String lastResponseId) {
        conversation.setLastResponseId(lastResponseId);
        conversation.setUpdatedAt(LocalDateTime.now());
        conversationRepository.save(conversation);
    }

    private List<SseEvent.CitationDto> parseCitations(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return jsonUtils.objectMapper().readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("解析 citations 失败", e);
            return List.of();
        }
    }

    private void sendEvent(SseEmitter emitter, SseEvent event) throws IOException {
        emitter.send(SseEmitter.event().data(event));
    }

    /**
     * 从来源节点列表获取原文
     *
     * @param sources 来源节点列表
     * @return 合并后的原文，多个来源用分隔符 "\n\n---\n\n" 连接
     */
    private String getNodeTextFromSources(List<NodeSource> sources) {
        if (sources == null || sources.isEmpty()) {
            return "";
        }

        StringBuilder combined = new StringBuilder();
        for (NodeSource source : sources) {
            String text = documentTreeService.getNodeText(source.getDocId(), source.getNodeId());
            if (text != null && !text.isEmpty()) {
                if (!combined.isEmpty()) {
                    combined.append("\n\n---\n\n");
                }
                combined.append(text);
            }
        }
        return combined.toString();
    }

    private String snippet(String text) {
        if (text == null) {
            return "";
        }
        String normalized = text.replaceAll("\\s+", " ").trim();
        return normalized.length() > 120 ? normalized.substring(0, 120) : normalized;
    }

    @Override
    public String generateTitle(String conversationId) {
        ConversationEntity conversation = conversationRepository.findByConversationId(conversationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CONVERSATION_NOT_FOUND,
                        "Conversation not found: " + conversationId));

        List<MessageEntity> allMessages = messageRepository.findByConversationIdOrderByCreatedAt(conversationId);

        if (allMessages.isEmpty()) {
            log.debug("会话无消息，使用默认标题, conversationId={}", conversationId);
            return "新对话";
        }

        List<MessageEntity> messages = allMessages.stream()
                .filter(m -> ROLE_USER.equals(m.getRole()))
                .limit(5)
                .toList();

        if (messages.isEmpty()) {
            log.debug("会话无用户消息，使用默认标题, conversationId={}", conversationId);
            return "新对话";
        }

        try {
            String prompt = promptTemplateService.buildTitleGenerationPrompt(messages);
            String title = llmClient.chat(prompt, null, 0.0).content();

            if (title != null && title.length() > 10) {
                title = title.substring(0, 10);
            }

            conversation.setTitle(title);
            conversation.setUpdatedAt(LocalDateTime.now());
            conversationRepository.save(conversation);
            log.info("生成会话标题成功, conversationId={}, title={}", conversationId, title);
            return title;
        } catch (Exception e) {
            log.warn("LLM 生成标题失败，使用降级策略, conversationId={}", conversationId, e);
            String fallbackTitle = messages.get(0).getContent().replaceAll("\\s+", " ").trim();
            if (fallbackTitle.length() > 10) {
                fallbackTitle = fallbackTitle.substring(0, 10);
            }
            conversation.setTitle(fallbackTitle);
            conversation.setUpdatedAt(LocalDateTime.now());
            conversationRepository.save(conversation);
            return fallbackTitle;
        }
    }

    @Override
    public void updateTitle(String conversationId, String title) {
        ConversationEntity conversation = findConversation(conversationId);
        conversation.setTitle(title);
        conversation.setUpdatedAt(LocalDateTime.now());
        conversationRepository.save(conversation);
        log.info("更新会话标题成功, conversationId={}, title={}", conversationId, title);
    }

    @Override
    public void smartChatStream(String conversationId, ChatRequest request, SseEmitter emitter, SecurityUserDetails userDetails) {
        String question = request.question();
        Long documentId = request.documentId();

        if (!StringUtils.hasText(question)) {
            throw new BusinessException(ErrorCode.QUESTION_EMPTY, "Question is required");
        }
        if (question.length() > 2000) {
            throw new BusinessException(ErrorCode.QUESTION_TOO_LONG, "Question exceeds maximum length of 2000 characters");
        }

        ConversationEntity conversation = findConversation(conversationId);

        // Check system access permission
        checkSystemAccess(conversation.getSystemId(), userDetails);

        boolean hasDocument = documentId != null;

        // 保存用户消息（如果有文档，记录文档ID）
        saveUserMessageWithDocument(conversationId, question, documentId);

        try {
            // 1. 意图识别
            sendEvent(emitter, SseEvent.stage("intent_detection", "running"));
            IntentResult intentResult = intentClassifier.classify(question, hasDocument);
            sendEvent(emitter, SseEvent.stage("intent_detection", "success"));

            // 发送意图检测结果
            sendEvaluationEvent(emitter, EvaluationSseEvent.intentDetected(
                    intentResult.intent().name(),
                    intentResult.confidence().name()
            ));

            // 2. 根据意图和置信度处理
            if (intentResult.needsClarification()) {
                // 需要澄清
                handleClarification(emitter, intentResult, hasDocument);
                emitter.complete();
                return;
            }

            if (intentResult.intent() == IntentType.REQUIREMENT_EVAL) {
                // 需求评估流程
                if (!hasDocument) {
                    // 没有文档但需要评估
                    sendEvaluationEvent(emitter, EvaluationSseEvent.clarificationNeeded(List.of(
                            EvaluationSseEvent.ClarificationOptionData.of(
                                    "上传需求文档",
                                    "请先上传需求文档后再进行评估",
                                    "UPLOAD_DOCUMENT",
                                    "A"
                            )
                    )));
                    emitter.complete();
                    return;
                }
                // 执行需求评估
                executeRequirementEvaluation(conversation, question, documentId, emitter);
            } else {
                // 普通问答流程 - 内联实现
                executeQuestionAnswer(conversation, question, emitter, userDetails);
            }

        } catch (Exception e) {
            log.error("智能对话处理失败, conversationId={}", conversationId, e);
            try {
                sendEvent(emitter, SseEvent.error(e.getMessage()));
            } catch (IOException ignored) {}
            emitter.complete();
        }
    }

    /**
     * 执行普通问答流程
     */
    private void executeQuestionAnswer(ConversationEntity conversation, String question, SseEmitter emitter, SecurityUserDetails userDetails) {
        String conversationId = conversation.getConversationId();
        Integer viewLevel = getViewLevel(conversation.getSystemId(), userDetails);

        try {
            sendEvent(emitter, SseEvent.stage("init", "running"));

            Instant startedAt = Instant.now();
            sendEvent(emitter, SseEvent.stage("init", "success"));
            sendEvent(emitter, SseEvent.stage("load_document", "running"));

            SystemKnowledgeTree systemTree = systemKnowledgeTreeService.getActiveTree(conversation.getSystemId());
            Map<String, TreeNode> nodeMap = treeNodeMapper.createNodeMap(systemTree.getStructure());

            sendEvent(emitter, SseEvent.stage("load_document", "success"));
            sendEvent(emitter, SseEvent.stage("search", "running"));

            // 树搜索是独立任务，不传递 previousResponseId（树搜索不需要对话上下文）
            TreeSearchResult result = treeSearchService.search(
                    systemTree.getStructure(), question, null, null);
            // 注意：treeSearch 返回的 responseId 仅用于日志/debug，不参与对话串联
            String searchResponseId = result.getResponseId();

            List<TreeNode> relevantNodes = new ArrayList<>();
            for (String nodeId : result.getNodeList()) {
                TreeNode node = nodeMap.get(nodeId);
                if (node != null) {
                    // Filter sources by user's view level
                    if (node.getSources() != null) {
                        List<NodeSource> filteredSources = node.getSources().stream()
                                .filter(source -> source.getSecurityLevel() == null || source.getSecurityLevel() <= viewLevel)
                                .toList();
                        node.setSources(filteredSources);
                    }
                    if (node.getSources() == null || !node.getSources().isEmpty()) {
                        relevantNodes.add(node);
                    }
                }
            }

            sendEvent(emitter, SseEvent.stage("search", "success"));

            List<Citation> citations;
            boolean grounded;
            StringBuilder answerBuilder = new StringBuilder();
            StringBuilder thinkingBuilder = new StringBuilder();
            String finalResponseId;

            // 获取对话的 lastResponseId（用于多轮对话上下文串联）
            // 第一轮: null, 第二轮及以后: 上一轮答案生成的 responseId
            String previousResponseId = conversation.getLastResponseId();

            if (relevantNodes.isEmpty()) {
                sendEvent(emitter, SseEvent.answer(FALLBACK_ANSWER));
                answerBuilder.append(FALLBACK_ANSWER);
                citations = List.of();
                grounded = false;
                // 没有答案生成，所以 finalResponseId 保持为 null（或沿用之前的值）
                finalResponseId = previousResponseId;
            } else {
                sendEvent(emitter, SseEvent.stage("thinking", "running"));

                // 直接使用系统树节点的 text 字段（已整合的最新内容）
                for (TreeNode node : relevantNodes) {
                    if (node.getText() == null || node.getText().isEmpty()) {
                        // 如果系统树节点的 text 为空，降级到从文档树获取
                        String originalText = getNodeTextFromSources(node.getSources());
                        node.setText(originalText);
                    }
                }

                // 答案生成是多轮对话主体，需要传递 previousResponseId 维持上下文
                finalResponseId = answerGenerationService.generateStream(
                        question, relevantNodes, null, previousResponseId,
                        thinkingDelta -> {
                            thinkingBuilder.append(thinkingDelta);
                            try { sendEvent(emitter, SseEvent.thinking(thinkingDelta)); }
                            catch (IOException e) { log.error("推送思考过程失败", e); }
                        },
                        textDelta -> {
                            answerBuilder.append(textDelta);
                            try { sendEvent(emitter, SseEvent.answer(textDelta)); }
                            catch (IOException e) { log.error("推送答案失败", e); }
                        }
                );

                sendEvent(emitter, SseEvent.stage("thinking", "success"));
                sendEvent(emitter, SseEvent.stage("answer", "success"));

                citations = relevantNodes.stream()
                        .map(node -> {
                            // Get docId and docName from the first source
                            Long docId = null;
                            String docName = null;
                            if (node.getSources() != null && !node.getSources().isEmpty()) {
                                NodeSource firstSource = node.getSources().get(0);
                                docId = firstSource.getDocId();
                                if (docId != null) {
                                    try {
                                        DocumentEntity doc = documentRepository.findById(docId).orElse(null);
                                        docName = doc != null ? doc.getDocName() : null;
                                    } catch (Exception e) {
                                        log.warn("Failed to get document name for docId={}", docId);
                                    }
                                }
                            }
                            return new Citation(
                                    node.getNodeId(),
                                    node.getTitle(),
                                    snippet(node.getSummary()),
                                    node.getSources(),
                                    docId,
                                    docName,
                                    node.getLineNum(),
                                    node.getLevel()
                            );
                        })
                        .toList();
                grounded = true;
            }

            if (!citations.isEmpty()) {
                sendEvent(emitter, SseEvent.citations(citations));
            }
            sendEvent(emitter, SseEvent.done(grounded));
            emitter.complete();

            int responseMs = (int) Duration.between(startedAt, Instant.now()).toMillis();
            log.info("对话完成, conversationId={}, responseMs={}ms, grounded={}", conversationId, responseMs, grounded);

            // 保存助手消息和更新 lastResponseId
            saveAssistantMessage(conversationId, answerBuilder.toString(), finalResponseId,
                    thinkingBuilder.toString(), citations);
            updateConversation(conversation, finalResponseId);

        } catch (Exception e) {
            log.error("对话处理失败, conversationId={}", conversationId, e);
            try { sendEvent(emitter, SseEvent.error(e.getMessage())); }
            catch (IOException ignored) {}
            emitter.complete();
        }
    }

    /**
     * 处理需要澄清的情况
     */
    private void handleClarification(SseEmitter emitter, IntentResult intentResult, boolean hasDocument) throws IOException {
        List<EvaluationSseEvent.ClarificationOptionData> options = new ArrayList<>();

        if (hasDocument) {
            // 有文档但意图不明确，提供两种选择
            options.add(EvaluationSseEvent.ClarificationOptionData.of(
                    "问答",
                    "基于文档内容回答问题",
                    IntentType.QUESTION.name(),
                    "A"
            ));
            options.add(EvaluationSseEvent.ClarificationOptionData.of(
                    "需求评估",
                    "评估需求的影响、冲突和可行性",
                    IntentType.REQUIREMENT_EVAL.name(),
                    "B"
            ));
        } else {
            // 没有文档但可能是需求评估意图
            options.add(EvaluationSseEvent.ClarificationOptionData.of(
                    "普通问答",
                    "询问系统现有功能",
                    IntentType.QUESTION.name(),
                    "A"
            ));
            options.add(EvaluationSseEvent.ClarificationOptionData.of(
                    "上传文档评估",
                    "上传需求文档后进行评估",
                    "UPLOAD_AND_EVAL",
                    "B"
            ));
        }

        sendEvaluationEvent(emitter, EvaluationSseEvent.clarificationNeeded(options));
    }

    /**
     * 执行需求评估
     */
    private void executeRequirementEvaluation(ConversationEntity conversation, String question,
                                              Long documentId, SseEmitter emitter) {
        String conversationId = conversation.getConversationId();

        try {
            // 加载系统知识树
            sendEvent(emitter, SseEvent.stage("load_system_tree", "running"));
            SystemKnowledgeTree systemTree = systemKnowledgeTreeService.getActiveTree(conversation.getSystemId());
            sendEvent(emitter, SseEvent.stage("load_system_tree", "success"));

            // 读取需求文档内容
            sendEvent(emitter, SseEvent.stage("load_document", "running"));
            String requirementContent = loadDocumentContent(documentId);
            if (requirementContent == null || requirementContent.isBlank()) {
                sendEvent(emitter, SseEvent.error("无法读取需求文档内容"));
                emitter.complete();
                return;
            }
            sendEvent(emitter, SseEvent.stage("load_document", "success"));

            // 执行四项检测
            StringBuilder combinedAnswer = new StringBuilder();
            combinedAnswer.append("正在为您进行需求评估分析...\n\n");

            EvaluationResult evaluationResult = requirementEvaluationService.evaluate(
                    requirementContent,
                    systemTree,
                    stage -> {
                        try {
                            sendEvaluationEvent(emitter, EvaluationSseEvent.evaluationStage(
                                    stage.getCategory(), "running"
                            ));
                        } catch (IOException e) {
                            log.error("发送评估阶段事件失败", e);
                        }
                    },
                    conversation.getLastResponseId()  // 传入上一次的 responseId
            );

            List<EvaluationReport> reports = evaluationResult.reports();

            // 发送评估结果
            for (EvaluationReport report : reports) {
                sendEvaluationEvent(emitter, EvaluationSseEvent.evaluationResult(report));
                combinedAnswer.append(report.categoryDisplayName())
                        .append(": ")
                        .append(report.riskLevelDisplayName())
                        .append("\n");
            }

            sendEvaluationEvent(emitter, EvaluationSseEvent.evaluationDone());
            sendEvent(emitter, SseEvent.done(true));
            emitter.complete();

            // 保存助手消息和更新 lastResponseId
            saveAssistantMessage(conversationId, combinedAnswer.toString(),
                    evaluationResult.lastResponseId(), null, List.of());
            updateConversation(conversation, evaluationResult.lastResponseId());

            log.info("需求评估完成, conversationId={}, reports={}, lastResponseId={}",
                    conversationId, reports.size(), evaluationResult.lastResponseId());

        } catch (Exception e) {
            log.error("需求评估失败, conversationId={}", conversationId, e);
            try {
                sendEvent(emitter, SseEvent.error("评估过程出现错误: " + e.getMessage()));
            } catch (IOException ignored) {}
            emitter.complete();
        }
    }

    /**
     * 读取文档内容
     */
    private String loadDocumentContent(Long documentId) {
        try {
            DocumentEntity document = documentRepository.findById(documentId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND, "Document not found"));

            Path filePath = Path.of(document.getFilePath());
            return markdownFileStorageService.read(filePath);
        } catch (Exception e) {
            log.error("读取文档内容失败, documentId={}", documentId, e);
            return null;
        }
    }

    /**
     * 发送评估事件
     */
    private void sendEvaluationEvent(SseEmitter emitter, EvaluationSseEvent event) throws IOException {
        emitter.send(SseEmitter.event().data(event));
    }

    /**
     * 保存用户消息（带文档ID）
     */
    private void saveUserMessageWithDocument(String conversationId, String question, Long documentId) {
        MessageEntity msg = new MessageEntity();
        msg.setConversationId(conversationId);
        msg.setRole(ROLE_USER);
        msg.setContent(question);
        if (documentId != null) {
            msg.setMetadata("{\"documentId\": " + documentId + "}");
        }
        msg.setCreatedAt(LocalDateTime.now());
        messageRepository.save(msg);
    }

    /**
     * 检查用户是否有权限访问系统
     */
    private void checkSystemAccess(Long systemId, SecurityUserDetails userDetails) {
        if (userDetails.getRole().name().equals("SUPER_ADMIN")) {
            return;
        }

        systemUserMappingRepository.findBySystemIdAndUserId(systemId, userDetails.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PERMISSION_DENIED, "无权限访问此系统"));
    }

    /**
     * 获取用户在系统中的查看等级
     */
    private Integer getViewLevel(Long systemId, SecurityUserDetails userDetails) {
        if (userDetails.getRole().name().equals("SUPER_ADMIN")) {
            return 5;
        }

        return systemUserMappingRepository.findBySystemIdAndUserId(systemId, userDetails.getId())
                .map(SystemUserMappingEntity::getViewLevel)
                .orElse(0);
    }
}
