package io.github.xiaoailazy.coexistree.shared.integration;

import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Base class for repository integration tests.
 * Uses @DataJpaTest for lightweight JPA testing with embedded H2.
 */
@DataJpaTest
@ActiveProfiles("test")
public abstract class AbstractRepositoryTest {
    // Common repository test configuration
}
