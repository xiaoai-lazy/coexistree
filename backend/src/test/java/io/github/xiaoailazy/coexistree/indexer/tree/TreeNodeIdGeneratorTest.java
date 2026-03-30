package io.github.xiaoailazy.coexistree.indexer.tree;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TreeNodeIdGeneratorTest {

    private final TreeNodeIdGenerator generator = new TreeNodeIdGenerator();

    @Test
    void shouldGenerateSequentialIds() {
        assertThat(generator.nextId()).isEqualTo("0001");
        assertThat(generator.nextId()).isEqualTo("0002");
        assertThat(generator.nextId()).isEqualTo("0003");
    }

    @Test
    void shouldResetCounter() {
        generator.nextId();
        generator.nextId();
        
        generator.reset();
        
        assertThat(generator.nextId()).isEqualTo("0001");
    }

    @Test
    void shouldFormatWithLeadingZeros() {
        for (int i = 0; i < 99; i++) {
            generator.nextId();
        }
        
        assertThat(generator.nextId()).isEqualTo("0100");
    }

    @Test
    void shouldFormatLargeNumbers() {
        for (int i = 0; i < 999; i++) {
            generator.nextId();
        }
        
        assertThat(generator.nextId()).isEqualTo("1000");
    }
}
