package io.github.xiaoailazy.coexistree.agent.context;

import io.github.xiaoailazy.coexistree.security.model.SecurityUserDetails;

/**
 * Agent 运行时用户上下文，在 Root Agent 和子 Agent 之间共享。
 */
public record AgentUserContext(
        Long userId,
        Long systemId,
        Integer viewLevel,
        String conversationId
) {

    /**
     * 从 SecurityUserDetails 构建上下文。
     */
    public static AgentUserContext fromUser(
            SecurityUserDetails user,
            Long systemId,
            Integer viewLevel,
            String conversationId
    ) {
        return new AgentUserContext(
                user.getId(),
                systemId,
                viewLevel,
                conversationId
        );
    }
}
