package io.github.xiaoailazy.coexistree.agent.context;

import io.github.xiaoailazy.coexistree.security.model.SecurityUserDetails;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentUserContextTest {

    @Mock
    private SecurityUserDetails userDetails;

    @Test
    void shouldCreateContextWithAllFields() {
        AgentUserContext ctx = new AgentUserContext(1L, 2L, 3, "conv-123");
        assertEquals(1L, ctx.userId());
        assertEquals(2L, ctx.systemId());
        assertEquals(3, ctx.viewLevel());
        assertEquals("conv-123", ctx.conversationId());
    }

    @Test
    void shouldCreateFromSecurityUserDetails() {
        when(userDetails.getId()).thenReturn(1L);

        AgentUserContext ctx = AgentUserContext.fromUser(userDetails, 2L, 3, "conv-123");
        assertEquals(1L, ctx.userId());
        assertEquals(2L, ctx.systemId());
        assertEquals(3, ctx.viewLevel());
        assertEquals("conv-123", ctx.conversationId());
    }
}
