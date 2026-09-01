package com.alpeca.wallet.ledger.service.implementation;

import com.alpeca.wallet.ledger.config.rabbit.WalletRabbitProperties;
import com.alpeca.wallet.ledger.entity.WalletUpdatedOutboxEvent;
import com.alpeca.wallet.ledger.event.WalletUpdatedEvent;
import com.alpeca.wallet.ledger.service.WalletUpdatedEventPublisher;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * RabbitMQ implementation of wallet update event publishing.
 */
@Component
class RabbitWalletUpdatedEventPublisher implements WalletUpdatedEventPublisher {

    private static final String TYPE_ID_HEADER = "type";

    private static final String WALLET_UPDATED_EVENT_TYPE = "WalletUpdatedEvent";

    private final RabbitTemplate rabbitTemplate;

    private final WalletRabbitProperties rabbitProperties;

    RabbitWalletUpdatedEventPublisher(
            RabbitTemplate rabbitTemplate,
            WalletRabbitProperties rabbitProperties
    ) {
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
        CorrelationData correlationData = new CorrelationData(outboxEvent.getTransactionId().toString());
        rabbitTemplate.convertAndSend(
                rabbitProperties.walletEventExchange(),
                "",
                event,
                message -> {
                    message.getMessageProperties()
                            .setHeader(TYPE_ID_HEADER, WALLET_UPDATED_EVENT_TYPE);
                    return message;
                },
                correlationData
        );
        waitForBrokerConfirmation(correlationData);
    }

    private void waitForBrokerConfirmation(CorrelationData correlationData) {
        try {
            CorrelationData.Confirm confirm = correlationData.getFuture()
                    .get(rabbitProperties.confirmTimeout().toMillis(), TimeUnit.MILLISECONDS);
            ReturnedMessage returnedMessage = correlationData.getReturned();
            if (returnedMessage != null) {
                throw new AmqpException(("""
                        RabbitMQ returned unroutable message: exchange=%s, routingKey=%s, replyCode=%d, replyText=%s
                        """).formatted(
                        returnedMessage.getExchange(),
                        returnedMessage.getRoutingKey(),
                        returnedMessage.getReplyCode(),
                        returnedMessage.getReplyText()
                ));
            }
            if (!confirm.ack()) {
                throw new AmqpException("RabbitMQ negatively acknowledged message publication: %s"
                        .formatted(confirm.reason()));
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AmqpException("Interrupted while waiting for RabbitMQ publisher confirmation", ex);
        } catch (ExecutionException ex) {
            throw new AmqpException("RabbitMQ publisher confirmation failed", ex);
        } catch (TimeoutException ex) {
            throw new AmqpException("RabbitMQ publisher confirmation timed out after %s"
                    .formatted(rabbitProperties.confirmTimeout()), ex);
        }
    }
}
