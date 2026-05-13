package io.github.xiaoailazy.coexistree.chat.controller;

import io.github.xiaoailazy.coexistree.chat.dto.AdminConversationResponse;
import io.github.xiaoailazy.coexistree.chat.entity.ConversationEntity;
import io.github.xiaoailazy.coexistree.chat.repository.ConversationRepository;
import io.github.xiaoailazy.coexistree.shared.api.ApiResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/sessions")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class SessionAuditController {

    private final ConversationRepository conversationRepository;

    public SessionAuditController(ConversationRepository conversationRepository) {
        this.conversationRepository = conversationRepository;
    }

    @GetMapping
    public ApiResponse<Page<AdminConversationResponse>> listConversations(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt"));
        Page<ConversationEntity> conversations = conversationRepository.findAll(pageable);

        Page<AdminConversationResponse> result = conversations.map(conv ->
                new AdminConversationResponse(
                        conv.getConversationId(),
                        conv.getSystemId(),
                        null,
                        conv.getTitle(),
                        null,
                        conv.getCreatedAt(),
                        conv.getUpdatedAt(),
                        0L
                )
        );

        return ApiResponse.success(result);
    }
}
