package io.github.xiaoailazy.coexistree.config.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 为每个 HTTP 请求注入 MDC 上下文：correlationId, userId, systemId, conversationId
 */
@Component
public class MdcFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        try {
            MDC.put("correlationId", UUID.randomUUID().toString().substring(0, 8));

            // 从 SecurityContext 获取 userId
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && auth.getPrincipal() != null) {
                if (auth.getPrincipal() instanceof io.github.xiaoailazy.coexistree.security.model.SecurityUserDetails user) {
                    MDC.put("userId", String.valueOf(user.getId()));
                }
            }

            // 从请求参数提取 systemId
            String systemId = request.getParameter("systemId");
            if (systemId != null && !systemId.isBlank()) {
                MDC.put("systemId", systemId);
            }

            // conversationId 可能在 URL path 中：/api/v1/chat/{conversationId}/stream
            String path = request.getRequestURI();
            String conversationId = extractConversationId(path);
            if (conversationId != null) {
                MDC.put("conversationId", conversationId);
            }

            filterChain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }

    private String extractConversationId(String path) {
        // /api/v1/chat/{conversationId}/stream
        // /api/v1/chat/{conversationId}
        if (path.startsWith("/api/v1/chat/")) {
            String rest = path.substring("/api/v1/chat/".length());
            int slashIndex = rest.indexOf('/');
            return slashIndex > 0 ? rest.substring(0, slashIndex) : rest;
        }
        return null;
    }
}
