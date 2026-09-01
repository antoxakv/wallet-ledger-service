package com.alpeca.wallet.ledger.service.implementation;

import com.alpeca.wallet.ledger.config.rabbit.WalletRabbitProperties;
import com.alpeca.wallet.ledger.entity.OperationStatus;
import com.alpeca.wallet.ledger.entity.OperationType;
import com.alpeca.wallet.ledger.entity.WalletLedger;
import com.alpeca.wallet.ledger.entity.WalletUpdatedOutboxEvent;
import com.alpeca.wallet.ledger.event.WalletUpdatedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.amqp.AmqpException;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.core.MessagePostProcessor;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;

@ExtendWith(MockitoExtension.class)
class RabbitWalletUpdatedEventPublisherTests {

    private static final String TYPE_HEADER = "type";

    private static final String WALLET_UPDATED_EVENT_TYPE = "WalletUpdatedEvent";

    private static final String EXCHANGE = "wallet.events";

    private static final UUID WALLET_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private static final UUID TRANSACTION_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private static final BigDecimal AMOUNT = BigDecimal.valueOf(10);

    private static final BigDecimal BALANCE_AFTER = BigDecimal.valueOf(110);

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private WalletRabbitProperties rabbitProperties;

    @InjectMocks
    private RabbitWalletUpdatedEventPublisher publisher;

    @Test
    void publishSendsWalletUpdatedEventToConfiguredExchangeAndWaitsForAck() {
        WalletUpdatedOutboxEvent outboxEvent = outboxEvent();
        when(rabbitProperties.walletEventExchange()).thenReturn(EXCHANGE);
        when(rabbitProperties.confirmTimeout()).thenReturn(Duration.ofSeconds(2));
        doAnswer(invocation -> {
            CorrelationData correlationData = invocation.getArgument(4);
            correlationData.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbitTemplate).convertAndSend(
                eq(EXCHANGE),
                eq(""),
                any(WalletUpdatedEvent.class),
                any(MessagePostProcessor.class),
                any(CorrelationData.class)
        );

        publisher.publish(outboxEvent);

        ArgumentCaptor<WalletUpdatedEvent> eventCaptor = ArgumentCaptor.forClass(WalletUpdatedEvent.class);
        ArgumentCaptor<MessagePostProcessor> postProcessorCaptor = ArgumentCaptor.forClass(MessagePostProcessor.class);
        ArgumentCaptor<CorrelationData> correlationDataCaptor = ArgumentCaptor.forClass(CorrelationData.class);
        verify(rabbitTemplate).convertAndSend(
                eq(EXCHANGE),
                eq(""),
                eventCaptor.capture(),
                postProcessorCaptor.capture(),
                correlationDataCaptor.capture()
        );
        assertThat(eventCaptor.getValue().walletId()).isEqualTo(WALLET_ID);
        assertThat(eventCaptor.getValue().transactionId()).isEqualTo(TRANSACTION_ID);
        assertThat(eventCaptor.getValue().typeOperation()).isEqualTo(OperationType.CREDIT);
        assertThat(eventCaptor.getValue().amount()).isEqualTo(AMOUNT);
        assertThat(eventCaptor.getValue().balanceAfter()).isEqualTo(BALANCE_AFTER);
        assertThat(eventCaptor.getValue().createdAt()).isEqualTo(outboxEvent.getEventCreatedAt());
        assertThat(correlationDataCaptor.getValue().getId()).isEqualTo(outboxEvent.getTransactionId().toString());
        Message message = postProcessorCaptor.getValue()
                .postProcessMessage(new Message(new byte[0], new MessageProperties()));
        assertThat((String) message.getMessageProperties().getHeader(TYPE_HEADER))
                .isEqualTo(WALLET_UPDATED_EVENT_TYPE);
        verify(rabbitProperties).walletEventExchange();
        verify(rabbitProperties).confirmTimeout();
        verifyNoMoreInteractions(rabbitTemplate, rabbitProperties);
    }

    @Test
    void publishFailsWhenBrokerNegativelyAcknowledgesMessage() {
        WalletUpdatedOutboxEvent outboxEvent = outboxEvent();
        when(rabbitProperties.walletEventExchange()).thenReturn(EXCHANGE);
        when(rabbitProperties.confirmTimeout()).thenReturn(Duration.ofSeconds(2));
        doAnswer(invocation -> {
            CorrelationData correlationData = invocation.getArgument(4);
            correlationData.getFuture().complete(new CorrelationData.Confirm(false, "exchange missing"));
            return null;
        }).when(rabbitTemplate).convertAndSend(
                eq(EXCHANGE),
                eq(""),
                any(WalletUpdatedEvent.class),
                any(MessagePostProcessor.class),
                any(CorrelationData.class)
        );

        assertThatThrownBy(() -> publisher.publish(outboxEvent))
                .isInstanceOf(AmqpException.class)
                .hasMessageContaining("negatively acknowledged")
                .hasMessageContaining("exchange missing");
    }

    @Test
    void publishFailsWhenBrokerReturnsUnroutableMessage() {
        WalletUpdatedOutboxEvent outboxEvent = outboxEvent();
        when(rabbitProperties.walletEventExchange()).thenReturn(EXCHANGE);
        when(rabbitProperties.confirmTimeout()).thenReturn(Duration.ofSeconds(2));
        doAnswer(invocation -> {
            CorrelationData correlationData = invocation.getArgument(4);
            correlationData.setReturned(new ReturnedMessage(
                    new Message(new byte[0], new MessageProperties()),
                    312,
                    "NO_ROUTE",
                    EXCHANGE,
                    ""
            ));
            correlationData.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbitTemplate).convertAndSend(
                eq(EXCHANGE),
                eq(""),
                any(WalletUpdatedEvent.class),
                any(MessagePostProcessor.class),
                any(CorrelationData.class)
        );

        assertThatThrownBy(() -> publisher.publish(outboxEvent))
                .isInstanceOf(AmqpException.class)
                .hasMessageContaining("unroutable")
                .hasMessageContaining("NO_ROUTE");
    }

    @Test
    void publishFailsWhenPublisherConfirmationTimesOut() {
        WalletUpdatedOutboxEvent outboxEvent = outboxEvent();
        when(rabbitProperties.walletEventExchange()).thenReturn(EXCHANGE);
        when(rabbitProperties.confirmTimeout()).thenReturn(Duration.ofMillis(1));

        assertThatThrownBy(() -> publisher.publish(outboxEvent))
                .isInstanceOf(AmqpException.class)
                .hasMessageContaining("timed out");
    }

    private WalletUpdatedOutboxEvent outboxEvent() {
        return new WalletUpdatedOutboxEvent(
                new WalletLedger(
                        TRANSACTION_ID,
                        WALLET_ID,
                        OperationType.CREDIT,
                        AMOUNT,
                        OperationStatus.SUCCESS,
                        BALANCE_AFTER
                )
        );
    }
}
