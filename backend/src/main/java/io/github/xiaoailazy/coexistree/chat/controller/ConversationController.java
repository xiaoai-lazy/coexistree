package io.github.xiaoailazy.coexistree.chat.controller;

import io.github.xiaoailazy.coexistree.shared.api.ApiResponse;
import io.github.xiaoailazy.coexistree.chat.dto.ChatRequest;
import io.github.xiaoailazy.coexistree.chat.dto.ConversationResponse;
import io.github.xiaoailazy.coexistree.chat.dto.CreateConversationRequest;
import io.github.xiaoailazy.coexistree.chat.dto.MessageResponse;
import io.github.xiaoailazy.coexistree.chat.service.ConversationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/conversations")
public class ConversationController {

    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @PostMapping
    public ApiResponse<ConversationResponse> create(@RequestBody CreateConversationRequest request) {
        return ApiResponse.success(conversationService.createConversation(request.systemId(), request.title()));
    }

    @GetMapping
    public ApiResponse<List<ConversationResponse>> list() {
        return ApiResponse.success(conversationService.listConversations());
    }

    @GetMapping("/{conversationId}")
    public ApiResponse<ConversationResponse> get(@PathVariable String conversationId) {
        return ApiResponse.success(conversationService.getConversation(conversationId));
    }

    @GetMapping("/{conversationId}/messages")
    public ApiResponse<List<MessageResponse>> getMessages(@PathVariable String conversationId) {
        return ApiResponse.success(conversationService.getMessages(conversationId));
    }

    @DeleteMapping("/{conversationId}")
    public ApiResponse<Void> delete(@PathVariable String conversationId) {
        conversationService.deleteConversation(conversationId);
        return ApiResponse.success(null);
    }

    @PostMapping("/{conversationId}/title")
    public ApiResponse<String> generateTitle(@PathVariable String conversationId) {
        String title = conversationService.generateTitle(conversationId);
        return ApiResponse.success(title);
    }

    /**
     * 智能对话 - 支持意图识别和需求评估
     */
    @PostMapping(value = "/{conversationId}/smart-chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter smartChat(@PathVariable String conversationId, @RequestBody ChatRequest request) {
        SseEmitter emitter = new SseEmitter(300_000L); // 5分钟超时，评估可能需要更长时间
        new Thread(() -> conversationService.smartChatStream(conversationId, request, emitter)).start();
        return emitter;
    }
}
