package com.alpeca.wallet.ledger.config.rabbit;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * RabbitMQ routing properties for wallet events.
 *
 * @param walletEventExchange exchange used to publish WalletUpdatedEvent messages.
 */
@Validated
@ConfigurationProperties(prefix = "wallet-ledger-service.rabbitmq")
public record WalletRabbitProperties(
        @NotBlank
        String walletEventExchange
) {
}
