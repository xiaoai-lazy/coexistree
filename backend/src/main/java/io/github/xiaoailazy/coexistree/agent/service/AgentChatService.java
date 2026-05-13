package io.github.xiaoailazy.coexistree.agent.service;

import io.github.xiaoailazy.coexistree.chat.dto.ChatRequest;
import io.github.xiaoailazy.coexistree.security.model.SecurityUserDetails;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface AgentChatService {

    void smartChatStream(
            String conversationId,
            ChatRequest request,
            SseEmitter emitter,
            SecurityUserDetails userDetails
    );
}
