package io.github.xiaoailazy.coexistree.agent.tools;

import io.github.xiaoailazy.coexistree.agent.context.AgentUserContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GetSecurityLevelToolTest {

    @Test
    void shouldReturnViewLevelFromContext() {
        GetSecurityLevelTool tool = new GetSecurityLevelTool();
        AgentUserContext context = new AgentUserContext(1L, 2L, 3, "conv-1");

        String result = tool.execute(context);

        assertEquals("当前用户的查看等级: 3/5", result);
    }

    @Test
    void shouldHandleMaxViewLevel() {
        GetSecurityLevelTool tool = new GetSecurityLevelTool();
        AgentUserContext context = new AgentUserContext(1L, 2L, 5, "conv-1");

        String result = tool.execute(context);

        assertEquals("当前用户的查看等级: 5/5", result);
    }

    @Test
    void shouldHandleNullViewLevel() {
        GetSecurityLevelTool tool = new GetSecurityLevelTool();
        AgentUserContext context = new AgentUserContext(1L, null, null, null);

        String result = tool.execute(context);

        assertTrue(result.contains("null"));
    }
}
