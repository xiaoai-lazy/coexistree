package io.github.xiaoailazy.coexistree.shared.util;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class SnowflakeIdGeneratorTest {

    @Test
    void shouldGenerateUniqueIds() {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator();
        var ids = new java.util.HashSet<Long>();
        for (int i = 0; i < 10000; i++) {
            ids.add(generator.nextId());
        }
        assertThat(ids).hasSize(10000);
    }

    @Test
    void shouldGeneratePositiveIds() {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator();
        for (int i = 0; i < 1000; i++) {
            assertThat(generator.nextId()).isPositive();
        }
    }

    @Test
    void shouldGenerateIncreasingIds() {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator();
        long previous = generator.nextId();
        for (int i = 0; i < 1000; i++) {
            long current = generator.nextId();
            assertThat(current).isGreaterThan(previous);
            previous = current;
        }
    }
}
