package com.alpeca.wallet.ledger.config.rabbit;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * RabbitMQ routing properties for wallet events.
 *
 * @param walletEventExchange exchange used to publish WalletUpdatedEvent messages.
 * @param confirmTimeout maximum time to wait for RabbitMQ publisher confirmation.
 */
@Validated
@ConfigurationProperties(prefix = "wallet-ledger-service.rabbitmq")
public record WalletRabbitProperties(
        @NotBlank
        String walletEventExchange,

        @NotNull
        @DurationMin(millis = 1)
        Duration confirmTimeout
) {
}
