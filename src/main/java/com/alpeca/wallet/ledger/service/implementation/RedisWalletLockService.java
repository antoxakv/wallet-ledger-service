package com.alpeca.wallet.ledger.service.implementation;

import com.alpeca.wallet.ledger.config.redis.WalletRedisProperties;
import com.alpeca.wallet.ledger.dto.WalletLock;
import com.alpeca.wallet.ledger.service.WalletLockService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Redis-backed wallet lock service.
 * <p>
 * Acquires locks with expiring Redis keys and releases them with a token check to avoid deleting a
 * lock owned by another process.
 */
@Service
class RedisWalletLockService implements WalletLockService {

    private static final Logger log = LoggerFactory.getLogger(RedisWalletLockService.class);

    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class
    );

    private final StringRedisTemplate redisTemplate;

    private final WalletRedisProperties properties;

    RedisWalletLockService(StringRedisTemplate redisTemplate, WalletRedisProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    @Override
    public Optional<WalletLock> tryLock(UUID walletId) {
        return tryLockWithRetry(lockKey(walletId), properties.lockTimeout());
    }

    @Override
    public void unlock(WalletLock walletLock) {
        redisTemplate.execute(UNLOCK_SCRIPT, List.of(walletLock.key()), walletLock.token());
    }

    private String lockKey(UUID walletId) {
        return properties.lockKeyPrefix() + walletId;
    }

    /**
     * Attempts to acquire a lock several times with the configured retry backoff.
     */
    private Optional<WalletLock> tryLockWithRetry(String key, Duration timeout) {
        int attempts = properties.lockRetryAttempts();
        for (int attempt = 1; attempt <= attempts; attempt++) {
            Optional<WalletLock> lock = tryLockOnce(key, timeout);
            if (lock.isPresent()) {
                return lock;
            }
            if (attempt < attempts && !sleepBeforeRetry()) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    /**
     * Attempts one Redis SET NX operation with a generated ownership token.
     */
    private Optional<WalletLock> tryLockOnce(String key, Duration timeout) {
        String token = UUID.randomUUID().toString();
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(key, token, timeout);
        if (Boolean.TRUE.equals(locked)) {
            return Optional.of(new WalletLock(key, token));
        }
        return Optional.empty();
    }

    /**
     * Waits before the next lock attempt and preserves the interrupted status when interrupted.
     */
    private boolean sleepBeforeRetry() {
        try {
            Thread.sleep(properties.lockRetryBackoff());
            return true;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while waiting before Redis lock retry", ex);
            return false;
        }
    }
}
