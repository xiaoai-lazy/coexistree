package io.github.xiaoailazy.coexistree.agent.observability;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Component
public class AgentExecutionLogger {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public void logStart(String agentName) {
        log.info("[{}] {} Agent 开始执行", ts(), agentName);
    }

    public void logToolCall(String agentName, String toolName, String args) {
        log.info("[{}]   {} → {}({})", ts(), agentName, toolName, truncate(args, 80));
    }

    public void logToolResult(String agentName, String toolName, long durationMs) {
        log.info("[{}]   {} ← {} 完成 ({}ms)", ts(), agentName, toolName, durationMs);
    }

    public void logComplete(String agentName, int toolCallCount, long totalMs) {
        log.info("[{}] {} Agent 完成, 总计: {} 次工具调用, {}ms", ts(), agentName, toolCallCount, totalMs);
    }

    public void logError(String agentName, String error) {
        log.error("[{}] {} Agent 错误: {}", ts(), agentName, error);
    }

    private String ts() {
        return LocalDateTime.now().format(FMT);
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "null";
        return s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }
}
