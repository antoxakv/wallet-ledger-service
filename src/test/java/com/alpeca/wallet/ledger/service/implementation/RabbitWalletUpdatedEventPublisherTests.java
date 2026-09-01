package com.alpeca.wallet.ledger.service.implementation;

import com.alpeca.wallet.ledger.config.rabbit.WalletRabbitProperties;
import com.alpeca.wallet.ledger.entity.OperationStatus;
import com.alpeca.wallet.ledger.entity.OperationType;
import com.alpeca.wallet.ledger.entity.WalletLedger;
import com.alpeca.wallet.ledger.entity.WalletUpdatedOutboxEvent;
import com.alpeca.wallet.ledger.event.WalletUpdatedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.core.MessagePostProcessor;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.eq;

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
    void publishSendsWalletUpdatedEventToConfiguredExchange() {
        WalletUpdatedOutboxEvent outboxEvent = new WalletUpdatedOutboxEvent(
                new WalletLedger(
                        TRANSACTION_ID,
                        WALLET_ID,
                        OperationType.CREDIT,
                        AMOUNT,
                        OperationStatus.SUCCESS,
                        BALANCE_AFTER
                )
        );
        when(rabbitProperties.walletEventExchange()).thenReturn(EXCHANGE);

        publisher.publish(outboxEvent);

        ArgumentCaptor<WalletUpdatedEvent> eventCaptor = ArgumentCaptor.forClass(WalletUpdatedEvent.class);
        ArgumentCaptor<MessagePostProcessor> postProcessorCaptor = ArgumentCaptor.forClass(MessagePostProcessor.class);
        verify(rabbitTemplate).convertAndSend(eq(EXCHANGE), eq(""), eventCaptor.capture(), postProcessorCaptor.capture());
        assertThat(eventCaptor.getValue().walletId()).isEqualTo(WALLET_ID);
        assertThat(eventCaptor.getValue().transactionId()).isEqualTo(TRANSACTION_ID);
        assertThat(eventCaptor.getValue().typeOperation()).isEqualTo(OperationType.CREDIT);
        assertThat(eventCaptor.getValue().amount()).isEqualTo(AMOUNT);
        assertThat(eventCaptor.getValue().balanceAfter()).isEqualTo(BALANCE_AFTER);
        assertThat(eventCaptor.getValue().createdAt()).isEqualTo(outboxEvent.getEventCreatedAt());
        Message message = postProcessorCaptor.getValue().postProcessMessage(new Message(new byte[0], new MessageProperties()));
        assertThat((String) message.getMessageProperties().getHeader(TYPE_HEADER))
                .isEqualTo(WALLET_UPDATED_EVENT_TYPE);
        verify(rabbitProperties).walletEventExchange();
        verifyNoMoreInteractions(rabbitTemplate, rabbitProperties);
    }
}
