package io.github.xiaoailazy.coexistree.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * 异步配置 - 使用 Java 21 虚拟线程
 * 虚拟线程适用于 I/O 密集型任务（如 LLM 调用、文件操作）
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * 文档任务执行器 - 每个任务一个虚拟线程
     * 虚拟线程特点：
     * - 轻量级（~几百字节 vs ~1MB 平台线程）
     * - I/O 阻塞时自动让出，不占用 OS 线程
     * - 可创建数百万个，无需池化
     */
    @Bean(name = "documentTaskExecutor")
    public Executor documentTaskExecutor() {
        ThreadFactory factory = Thread.ofVirtual()
                .name("document-task-", 1)
                .factory();
        return Executors.newThreadPerTaskExecutor(factory);
    }
}

