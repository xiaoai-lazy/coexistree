package io.github.xiaoailazy.coexistree.knowledge.lock;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 系统树 PostgreSQL advisory xact lock 集成测试。
 *
 * <p><b>执行路径说明（二选一，本项目固定如下）：</b>使用 {@code @SpringBootTest} + {@code test} profile，
 * 数据源为 {@code application-test.yml} 中的 Testcontainers PostgreSQL（{@code jdbc:tc:postgresql:16}）。
 * 不使用 {@code @Disabled("PostgreSQL only")} + 本地 Postgres 的 {@code @DataJpaTest} 路径。
 */
@SpringBootTest
@ActiveProfiles("test")
class SystemTreeAdvisoryLockSupportTest {

    @Autowired
    private SystemTreeAdvisoryLockSupport advisoryLockSupport;

    @Autowired
    private EntityManager entityManager;

    @Test
    void advisoryLockSql_matchesDesignFormula() throws Exception {
        long systemId = 42L;
        BigInteger key = advisoryLockSupport.lockKeyForSystem(systemId);
        assertThat(key).isNotNull();

        String sqlHex =
                "SELECT substring(md5('coexistree:system_tree:' || cast(:sid as text)), 1, 16) as h";
        Object row = entityManager
                .createNativeQuery(sqlHex)
                .setParameter("sid", systemId)
                .getSingleResult();
        assertThat(row).isInstanceOf(String.class);
        String hex16 = (String) row;
        assertThat(new BigInteger(hex16, 16)).isEqualTo(key);
    }

    @Test
    @Transactional
    void acquireTransactionalLock_runsPgAdvisoryXactLockWithoutError() throws Exception {
        advisoryLockSupport.acquireTransactionalLockForSystem(42L);
    }
}
