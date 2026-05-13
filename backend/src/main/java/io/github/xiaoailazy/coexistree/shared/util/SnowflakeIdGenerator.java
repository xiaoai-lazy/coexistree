package io.github.xiaoailazy.coexistree.shared.util;

import org.springframework.stereotype.Component;

/**
 * Snowflake ID 生成器，适用于 session_events 表的 id 字段。
 * 64 位结构：41bit 时间戳 + 10bit 机器标识 + 12bit 序列号
 */
@Component
public class SnowflakeIdGenerator {

    private static final long EPOCH = 1700000000000L;
    private static final long WORKER_ID_BITS = 10L;
    private static final long SEQUENCE_BITS = 12L;
    private static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);
    private static final long MAX_SEQUENCE = ~(-1L << SEQUENCE_BITS);
    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;

    private final long workerId;
    private long sequence = 0L;
    private long lastTimestamp = -1L;

    public SnowflakeIdGenerator() {
        this.workerId = generateWorkerId();
    }

    private static long generateWorkerId() {
        return ProcessHandle.current().pid() % 1024;
    }

    public synchronized long nextId() {
        long timestamp = System.currentTimeMillis() - EPOCH;
        if (timestamp < lastTimestamp) {
            throw new IllegalStateException("Clock moved backwards, refusing to generate id");
        }
        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & MAX_SEQUENCE;
            if (sequence == 0) {
                while ((System.currentTimeMillis() - EPOCH) == lastTimestamp) {
                    // busy wait
                }
                timestamp = System.currentTimeMillis() - EPOCH;
            }
        } else {
            sequence = 0L;
        }
        lastTimestamp = timestamp;
        return (timestamp << TIMESTAMP_SHIFT)
                | (workerId << WORKER_ID_SHIFT)
                | sequence;
    }
}
