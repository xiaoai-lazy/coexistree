package io.github.xiaoailazy.coexistree.change.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class SystemChangeRecordRepositoryTest {

    @Autowired
    private SystemChangeRecordRepository repository;

    @Test
    void findByIdReturnsEmptyForNegativeOne() {
        assertThat(repository.findById(-1L)).isEmpty();
    }
}
