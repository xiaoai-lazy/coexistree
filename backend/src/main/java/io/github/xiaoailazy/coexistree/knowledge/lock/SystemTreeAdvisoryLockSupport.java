package io.github.xiaoailazy.coexistree.knowledge.lock;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * PostgreSQL advisory transaction lock for a single system knowledge tree (design §3.3).
 */
@Component
public class SystemTreeAdvisoryLockSupport {

    private final EntityManager entityManager;

    public SystemTreeAdvisoryLockSupport(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public BigInteger lockKeyForSystem(long systemId) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] digest = md.digest(
                ("coexistree:system_tree:" + systemId).getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        String hex16 = sb.substring(0, 16);
        return new BigInteger(hex16, 16);
    }

    /**
     * Blocks until the advisory lock is acquired; released automatically at transaction end.
     */
    @Transactional
    public void acquireTransactionalLockForSystem(long systemId) throws NoSuchAlgorithmException {
        long k = lockKeyForSystem(systemId).longValue();
        entityManager
                .createNativeQuery("SELECT pg_advisory_xact_lock(:k)")
                .setParameter("k", k)
                .getSingleResult();
    }
}
