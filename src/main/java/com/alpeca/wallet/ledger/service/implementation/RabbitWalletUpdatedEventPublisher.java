package com.alpeca.wallet.ledger.service.implementation;

import com.alpeca.wallet.ledger.config.rabbit.WalletRabbitProperties;
import com.alpeca.wallet.ledger.entity.WalletUpdatedOutboxEvent;
import com.alpeca.wallet.ledger.event.WalletUpdatedEvent;
import com.alpeca.wallet.ledger.service.WalletUpdatedEventPublisher;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * RabbitMQ implementation of wallet update event publishing.
 */
@Component
class RabbitWalletUpdatedEventPublisher implements WalletUpdatedEventPublisher {

    private static final String TYPE_ID_HEADER = "type";

    private static final String WALLET_UPDATED_EVENT_TYPE = "WalletUpdatedEvent";

    private final RabbitTemplate rabbitTemplate;

    private final WalletRabbitProperties rabbitProperties;

    RabbitWalletUpdatedEventPublisher(RabbitTemplate rabbitTemplate, WalletRabbitProperties rabbitProperties) {
        this.rabbitTemplate = rabbitTemplate;
        this.rabbitProperties = rabbitProperties;
    }

    @Override
    public void publish(WalletUpdatedOutboxEvent outboxEvent) {
        WalletUpdatedEvent event = new WalletUpdatedEvent(
                outboxEvent.getWalletId(),
                outboxEvent.getTransactionId(),
                outboxEvent.getTypeOperation(),
                outboxEvent.getAmount(),
                outboxEvent.getEventCreatedAt(),
                outboxEvent.getBalanceAfter()
        );
        rabbitTemplate.convertAndSend(
                rabbitProperties.walletEventExchange(),
                "",
                event,
                message -> {
                    message.getMessageProperties()
                            .setHeader(TYPE_ID_HEADER, WALLET_UPDATED_EVENT_TYPE);
                    return message;
                }
        );
    }
}
