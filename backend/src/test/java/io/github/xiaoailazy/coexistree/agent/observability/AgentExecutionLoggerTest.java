package io.github.xiaoailazy.coexistree.agent.observability;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;

class AgentExecutionLoggerTest {

    private final AgentExecutionLogger logger = new AgentExecutionLogger();

    @Test
    void shouldLogStartWithoutError() {
        assertThatCode(() -> logger.logStart("test-agent")).doesNotThrowAnyException();
    }

    @Test
    void shouldLogToolCallWithoutError() {
        assertThatCode(() -> logger.logToolCall("test-agent", "search_tree", "{\"query\": \"test\"}"))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldLogToolCallWithLongArgs() {
        String longArgs = "x".repeat(200);
        assertThatCode(() -> logger.logToolCall("test-agent", "tool", longArgs))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldLogToolResultWithoutError() {
        assertThatCode(() -> logger.logToolResult("test-agent", "search_tree", 150))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldLogCompleteWithoutError() {
        assertThatCode(() -> logger.logComplete("test-agent", 5, 3000))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldLogErrorWithoutError() {
        assertThatCode(() -> logger.logError("test-agent", "something went wrong"))
                .doesNotThrowAnyException();
    }
}
