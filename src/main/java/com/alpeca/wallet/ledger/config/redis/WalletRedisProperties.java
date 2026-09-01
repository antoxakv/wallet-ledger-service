package com.alpeca.wallet.ledger.config.redis;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Redis-backed wallet locking and cache properties.
 *
 * @param lockKeyPrefix key prefix for exclusive wallet operation locks.
 * @param lockTimeout time-to-live for exclusive wallet operation locks.
 * @param lockRetryAttempts number of attempts to acquire a Redis lock.
 * @param lockRetryBackoff delay between Redis lock acquisition attempts.
 */
@Validated
@ConfigurationProperties(prefix = "wallet-ledger-service.redis")
public record WalletRedisProperties(
		@NotBlank
		String lockKeyPrefix,

		@NotNull
		@DurationMin(seconds = 1)
		Duration lockTimeout,

		@Min(1)
		int lockRetryAttempts,

		@NotNull
		@DurationMin(millis = 1)
		Duration lockRetryBackoff
) {
}
