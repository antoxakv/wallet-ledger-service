package com.alpeca.wallet.ledger.service.implementation;

import com.alpeca.wallet.ledger.config.publisher.WalletUpdatedOutboxPublisherProperties;
import com.alpeca.wallet.ledger.entity.WalletUpdatedOutboxEvent;
import com.alpeca.wallet.ledger.repository.WalletUpdatedOutboxRepository;
import com.alpeca.wallet.ledger.service.WalletUpdatedEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Scheduled publisher for wallet update outbox events.
 * <p>
 * Claims ready events in batches, publishes them through the configured publisher and updates their
 * status for retry, exhaustion or successful publication.
 */
@Component
class WalletUpdatedOutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(WalletUpdatedOutboxPublisher.class);

    private final WalletUpdatedOutboxRepository outboxRepository;

    private final WalletUpdatedEventPublisher walletUpdatedEventPublisher;

    private final WalletUpdatedOutboxPublisherProperties properties;

    WalletUpdatedOutboxPublisher(
            WalletUpdatedOutboxRepository outboxRepository,
            WalletUpdatedEventPublisher walletUpdatedEventPublisher,
            WalletUpdatedOutboxPublisherProperties properties
    ) {
        this.outboxRepository = outboxRepository;
        this.walletUpdatedEventPublisher = walletUpdatedEventPublisher;
        this.properties = properties;
    }

    /**
     * Claims and publishes a batch of ready wallet update events.
     */
    @Scheduled(fixedDelayString = "${wallet-ledger-service.publisher.wallet-updated-outbox.fixed-delay-ms}")
    void publishReadyEvents() {
        OffsetDateTime now = OffsetDateTime.now();
        List<WalletUpdatedOutboxEvent> events = outboxRepository.claimReadyEvents(
                properties.batchSize(),
                properties.maxAttempts(),
                now,
                now.plus(properties.processingLockTtl())
        );
        for (WalletUpdatedOutboxEvent event : events) {
            publish(event);
        }
        if (!events.isEmpty()) {
            outboxRepository.saveAll(events);
        }
    }

    /**
     * Publishes one outbox event and updates the outbox record status according to the result.
     */
    private void publish(WalletUpdatedOutboxEvent event) {
        try {
            walletUpdatedEventPublisher.publish(event);
            event.markPublished(OffsetDateTime.now());
        } catch (RuntimeException exception) {
            if (event.getAttempts() >= properties.maxAttempts()) {
                log.error(
                        "Wallet updated outbox event exhausted after {} attempts: transactionId={}, walletId={}",
                        event.getAttempts(),
                        event.getTransactionId(),
                        event.getWalletId(),
                        exception
                );
                event.markExhausted(exception);
            } else {
                log.warn(
                        "Wallet updated outbox event publish failed: transactionId={}, walletId={}, "
                                + "attempt={}, maxAttempts={}",
                        event.getTransactionId(),
                        event.getWalletId(),
                        event.getAttempts(),
                        properties.maxAttempts(),
                        exception
                );
                event.markFailed(exception, OffsetDateTime.now(), properties.retryDelay());
            }
        }
    }
}
