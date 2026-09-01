package com.alpeca.wallet.ledger.service.implementation;

import com.alpeca.wallet.ledger.config.redis.WalletRedisProperties;
import com.alpeca.wallet.ledger.dto.WalletLock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.redis.test.autoconfigure.DataRedisTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataRedisTest(properties = {
        "wallet-ledger-service.redis.lock-key-prefix=wallet:lock:",
        "wallet-ledger-service.redis.lock-timeout=10s",
        "wallet-ledger-service.redis.lock-retry-attempts=1",
        "wallet-ledger-service.redis.lock-retry-backoff=50ms"
})
@EnableConfigurationProperties(WalletRedisProperties.class)
@Import(RedisWalletLockService.class)
@Testcontainers
class RedisWalletLockServiceTests {

    private static final UUID WALLET_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private static final String LOCK_KEY = "wallet:lock:" + WALLET_ID;

    @SuppressWarnings("resource")
    @Container
    @ServiceConnection(name = "redis")
    private static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:8.10.1"))
            .withExposedPorts(6379)
            .withEnv("TZ", "UTC")
            .withCommand(
                    "redis-server",
                    "--save",
                    "",
                    "--appendonly",
                    "no",
                    "--maxmemory",
                    "256mb",
                    "--maxmemory-policy",
                    "volatile-lru"
            );

    @Autowired
    private RedisWalletLockService service;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @AfterEach
    void cleanRedis() {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    @Test
    void tryLockCreatesLockKeyWithTokenAndTtl() {
        Optional<WalletLock> lock = service.tryLock(WALLET_ID);

        assertThat(lock).isPresent();
        assertThat(lock.get().key()).isEqualTo(LOCK_KEY);
        assertThat(lock.get().token()).isNotBlank();
        assertThat(redisTemplate.opsForValue().get(LOCK_KEY)).isEqualTo(lock.get().token());
        assertThat(redisTemplate.getExpire(LOCK_KEY)).isBetween(1L, 10L);
    }

    @Test
    void tryLockReturnsEmptyWhenWalletIsAlreadyLocked() {
        WalletLock lock = service.tryLock(WALLET_ID).orElseThrow();

        Optional<WalletLock> secondLock = service.tryLock(WALLET_ID);

        assertThat(secondLock).isEmpty();
        assertThat(redisTemplate.opsForValue().get(LOCK_KEY)).isEqualTo(lock.token());
    }

    @Test
    void unlockDeletesLockWhenTokenMatches() {
        WalletLock lock = service.tryLock(WALLET_ID).orElseThrow();

        service.unlock(lock);

        assertThat(redisTemplate.hasKey(LOCK_KEY)).isFalse();
    }

    @Test
    void unlockDoesNotDeleteLockWhenTokenDoesNotMatch() {
        WalletLock lock = service.tryLock(WALLET_ID).orElseThrow();

        service.unlock(new WalletLock(lock.key(), "another-token"));

        assertThat(redisTemplate.opsForValue().get(LOCK_KEY)).isEqualTo(lock.token());
    }
}
