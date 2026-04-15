package io.github.xiaoailazy.coexistree.shared.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LlmCallContextTest {

    @AfterEach
    void tearDown() {
        LlmCallContext.clear();
    }

    @Test
    void shouldReturnEmptyWhenNotSet() {
        assertThat(LlmCallContext.get()).isEmpty();
    }

    @Test
    void shouldStoreAndRetrieveContext() {
        LlmCallContext.set("BASELINE_MERGE", 1L, 1L, 42L);

        var ctx = LlmCallContext.get();
        assertThat(ctx).isPresent();
        assertThat(ctx.get().scenario()).isEqualTo("BASELINE_MERGE");
        assertThat(ctx.get().documentId()).isEqualTo(1L);
        assertThat(ctx.get().systemId()).isEqualTo(1L);
        assertThat(ctx.get().userId()).isEqualTo(42L);
    }

    @Test
    void shouldClearContext() {
        LlmCallContext.set("SCENARIO", 1L, 1L, 1L);
        LlmCallContext.clear();
        assertThat(LlmCallContext.get()).isEmpty();
    }

    @Test
    void shouldSupportNullUserId() {
        LlmCallContext.set("ASYNC_TASK", 1L, 1L, null);

        var ctx = LlmCallContext.get();
        assertThat(ctx).isPresent();
        assertThat(ctx.get().userId()).isNull();
    }

    @Test
    void shouldAllowOverwrite() {
        LlmCallContext.set("FIRST", 1L, 1L, 1L);
        LlmCallContext.set("SECOND", 2L, 2L, 2L);

        var ctx = LlmCallContext.get();
        assertThat(ctx.get().scenario()).isEqualTo("SECOND");
    }
}
