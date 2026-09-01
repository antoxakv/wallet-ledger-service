package com.alpeca.wallet.ledger.service.implementation;

import com.alpeca.wallet.ledger.config.publisher.WalletUpdatedOutboxPublisherProperties;
import com.alpeca.wallet.ledger.entity.OperationStatus;
import com.alpeca.wallet.ledger.entity.OperationType;
import com.alpeca.wallet.ledger.entity.WalletLedger;
import com.alpeca.wallet.ledger.entity.WalletUpdatedOutboxEvent;
import com.alpeca.wallet.ledger.entity.WalletUpdatedOutboxStatus;
import com.alpeca.wallet.ledger.repository.WalletUpdatedOutboxRepository;
import com.alpeca.wallet.ledger.service.WalletUpdatedEventPublisher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WalletUpdatedOutboxPublisherTests {

    private static final int MAX_ATTEMPTS = 3;

    private static final Duration PROCESSING_LOCK_TTL = Duration.ofSeconds(30);

    private static final Duration RETRY_DELAY = Duration.ofSeconds(10);

    private static final UUID WALLET_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private static final UUID TRANSACTION_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private static final RuntimeException PUBLISH_EXCEPTION = new RuntimeException("broker down");

    @Mock
    private WalletUpdatedOutboxRepository outboxRepository;

    @Mock
    private WalletUpdatedEventPublisher walletUpdatedEventPublisher;

    @Mock
    private WalletUpdatedOutboxPublisherProperties properties;

    @InjectMocks
    private WalletUpdatedOutboxPublisher publisher;

    @AfterEach
    void tearDown() {
        verifyNoMoreInteractions(outboxRepository, walletUpdatedEventPublisher, properties);
    }

    @Test
    void publishReadyEventsDoesNotSaveWhenNoEventsWereClaimed() {
        givenReadyOutboxEventsClaimed(List.of());

        publisher.publishReadyEvents();

        verify(properties).maxAttempts();
        ArgumentCaptor<OffsetDateTime> nowCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
        ArgumentCaptor<OffsetDateTime> lockedUntilCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(properties).processingLockTtl();
        verify(outboxRepository).claimReadyEvents(
                eq(10),
                eq(MAX_ATTEMPTS),
                nowCaptor.capture(),
                lockedUntilCaptor.capture()
        );
        assertThat(lockedUntilCaptor.getValue()).isEqualTo(nowCaptor.getValue().plus(PROCESSING_LOCK_TTL));
    }

    @Test
    void publishReadyEventsMarksPublishedEventAndSavesIt() {
        WalletUpdatedOutboxEvent event = outboxEvent();
        givenReadyOutboxEventsClaimed(List.of(event));

        publisher.publishReadyEvents();

        assertThat(ReflectionTestUtils.getField(event, "status")).isEqualTo(WalletUpdatedOutboxStatus.PUBLISHED);
        assertThat(ReflectionTestUtils.getField(event, "publishedAt")).isNotNull();
        assertThat(ReflectionTestUtils.getField(event, "lastError")).isNull();
        assertThat(ReflectionTestUtils.getField(event, "lockedUntil")).isNull();

        verify(walletUpdatedEventPublisher).publish(event);
        verify(outboxRepository).saveAll(List.of(event));
        verify(properties).maxAttempts();
        verifyReadyOutboxEventsClaimed();
    }

    @ParameterizedTest
    @MethodSource("failedPublicationData")
    void publishReadyEventsMarksFailedPublicationAndSavesIt(
            int attempts,
            WalletUpdatedOutboxStatus status,
            int maxAttemptsInvocations,
            boolean retried
    ) {
        WalletUpdatedOutboxEvent event = outboxEvent();
        ReflectionTestUtils.setField(event, "attempts", attempts);
        givenReadyOutboxEventsClaimed(List.of(event));
        doThrow(PUBLISH_EXCEPTION).when(walletUpdatedEventPublisher).publish(event);
        if (retried) {
            when(properties.retryDelay()).thenReturn(RETRY_DELAY);
        }

        OffsetDateTime now = OffsetDateTime.now();
        publisher.publishReadyEvents();

        assertThat(ReflectionTestUtils.getField(event, "status")).isEqualTo(status);
        assertThat(ReflectionTestUtils.getField(event, "lastError")).isEqualTo(PUBLISH_EXCEPTION.getMessage());
        assertThat(ReflectionTestUtils.getField(event, "lockedUntil")).isNull();
        if (retried) {
            assertThat((OffsetDateTime) ReflectionTestUtils.getField(event, "nextAttemptAt"))
                    .isAfterOrEqualTo(now.plus(RETRY_DELAY));
            verify(properties).retryDelay();
        }
        verify(walletUpdatedEventPublisher).publish(event);
        verify(outboxRepository).saveAll(List.of(event));
        verify(properties, times(maxAttemptsInvocations)).maxAttempts();
        verifyReadyOutboxEventsClaimed();
    }

    private static Stream<Arguments> failedPublicationData() {
        return Stream.of(
                Arguments.of(1, WalletUpdatedOutboxStatus.FAILED, 3, true),
                Arguments.of(MAX_ATTEMPTS, WalletUpdatedOutboxStatus.EXHAUSTED, 2, false)
        );
    }

    private void givenReadyOutboxEventsClaimed(List<WalletUpdatedOutboxEvent> events) {
        when(properties.maxAttempts()).thenReturn(MAX_ATTEMPTS);
        when(properties.processingLockTtl()).thenReturn(PROCESSING_LOCK_TTL);
        when(outboxRepository.claimReadyEvents(
                eq(10),
                eq(MAX_ATTEMPTS),
                any(OffsetDateTime.class),
                any(OffsetDateTime.class)
        )).thenReturn(events);
    }

    private void verifyReadyOutboxEventsClaimed() {
        verify(properties).processingLockTtl();
        verify(outboxRepository).claimReadyEvents(
                eq(10),
                eq(MAX_ATTEMPTS),
                any(OffsetDateTime.class),
                any(OffsetDateTime.class)
        );
    }

    private static WalletUpdatedOutboxEvent outboxEvent() {
        return new WalletUpdatedOutboxEvent(
                new WalletLedger(
                        TRANSACTION_ID,
                        WALLET_ID,
                        OperationType.CREDIT,
                        BigDecimal.TEN,
                        OperationStatus.SUCCESS,
                        BigDecimal.valueOf(110)
                )
        );
    }
}
