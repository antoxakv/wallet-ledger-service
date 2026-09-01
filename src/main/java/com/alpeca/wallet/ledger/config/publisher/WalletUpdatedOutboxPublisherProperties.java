package com.alpeca.wallet.ledger.config.publisher;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Wallet updated outbox publisher scheduling and retry properties.
 *
 * @param processingLockTtl time-to-live for claimed outbox events.
 * @param retryDelay delay before retrying a failed broker publication.
 * @param maxAttempts maximum number of broker publication attempts before exhaustion.
 */
@Validated
@ConfigurationProperties(prefix = "wallet-ledger-service.publisher.wallet-updated-outbox")
public record WalletUpdatedOutboxPublisherProperties(
		@NotNull
		@DurationMin(seconds = 1)
		Duration processingLockTtl,

		@NotNull
		@DurationMin(seconds = 1)
		Duration retryDelay,

		@Min(1)
		int maxAttempts
) {
}
