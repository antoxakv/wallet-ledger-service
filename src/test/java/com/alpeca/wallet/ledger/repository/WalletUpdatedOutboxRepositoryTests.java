package com.alpeca.wallet.ledger.repository;

import com.alpeca.wallet.ledger.entity.OperationStatus;
import com.alpeca.wallet.ledger.entity.OperationType;
import com.alpeca.wallet.ledger.entity.WalletUpdatedOutboxEvent;
import com.alpeca.wallet.ledger.entity.WalletUpdatedOutboxStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class WalletUpdatedOutboxRepositoryTests extends PostgresRepositoryTestBase {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-08-31T10:00:00Z");

    private static final OffsetDateTime LOCKED_UNTIL = NOW.plusMinutes(1);

    private static final OffsetDateTime WALLET_UPDATED_AT = OffsetDateTime.parse("2026-08-31T09:00:00Z");

    private static final OffsetDateTime READY_NEXT_ATTEMPT_AT = NOW.minusMinutes(1);

    private static final OffsetDateTime FUTURE_NEXT_ATTEMPT_AT = NOW.plusSeconds(1);

    private static final OffsetDateTime EXPIRED_LOCKED_UNTIL = NOW.minusSeconds(1);

    private static final OffsetDateTime ACTIVE_LOCKED_UNTIL = NOW.plusSeconds(1);

    private static final OffsetDateTime FIRST_READY_CREATED_AT = NOW.minusMinutes(3);

    private static final OffsetDateTime SECOND_READY_CREATED_AT = NOW.minusMinutes(2);

    private static final OffsetDateTime THIRD_READY_CREATED_AT = NOW.minusMinutes(1);

    private static final BigDecimal WALLET_BALANCE = BigDecimal.ZERO;

    private static final BigDecimal EVENT_AMOUNT = BigDecimal.TEN;

    private static final BigDecimal EVENT_BALANCE_AFTER = BigDecimal.TEN;

    private static final UUID WALLET_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private static final String OPERATION_TYPE_CREDIT = OperationType.CREDIT.getValue();

    private static final String OPERATION_STATUS_SUCCESS = OperationStatus.SUCCESS.getValue();

    private static final WalletUpdatedOutboxStatus OUTBOX_STATUS_PENDING = WalletUpdatedOutboxStatus.PENDING;

    private static final WalletUpdatedOutboxStatus OUTBOX_STATUS_PROCESSING = WalletUpdatedOutboxStatus.PROCESSING;

    private static final WalletUpdatedOutboxStatus OUTBOX_STATUS_FAILED = WalletUpdatedOutboxStatus.FAILED;

    private static final String PREVIOUS_ERROR = "previous error";

    private static final int CLAIM_LIMIT = 2;

    private static final int MAX_ATTEMPTS = 3;

    private static final int NO_ATTEMPTS = 0;

    private static final int ONE_ATTEMPT = 1;

    private static final int TWO_ATTEMPTS = 2;

    private static final int MAX_ATTEMPTS_REACHED = 3;

    @Autowired
    private WalletUpdatedOutboxRepository walletUpdatedOutboxRepository;

    @Test
    void claimReadyEventsClaimsEligibleEventsUpToLimit() {
        insertWallet();
        UUID firstReadyTransactionId = insertOutboxEvent(
                OUTBOX_STATUS_PENDING,
                NO_ATTEMPTS,
                READY_NEXT_ATTEMPT_AT,
                null,
                PREVIOUS_ERROR,
                FIRST_READY_CREATED_AT
        );
        UUID secondReadyTransactionId = insertOutboxEvent(
                OUTBOX_STATUS_FAILED,
                ONE_ATTEMPT,
                READY_NEXT_ATTEMPT_AT,
                null,
                PREVIOUS_ERROR,
                SECOND_READY_CREATED_AT
        );
        UUID thirdReadyTransactionId = insertOutboxEvent(
                OUTBOX_STATUS_PROCESSING,
                ONE_ATTEMPT,
                READY_NEXT_ATTEMPT_AT,
                EXPIRED_LOCKED_UNTIL,
                PREVIOUS_ERROR,
                THIRD_READY_CREATED_AT
        );
        UUID futureRetryTransactionId = insertOutboxEvent(
                OUTBOX_STATUS_FAILED,
                ONE_ATTEMPT,
                FUTURE_NEXT_ATTEMPT_AT,
                null,
                null,
                NOW
        );
        UUID lockedTransactionId = insertOutboxEvent(
                OUTBOX_STATUS_PROCESSING,
                ONE_ATTEMPT,
                READY_NEXT_ATTEMPT_AT,
                ACTIVE_LOCKED_UNTIL,
                null,
                NOW
        );
        UUID maxAttemptsReachedTransactionId = insertOutboxEvent(
                OUTBOX_STATUS_PENDING,
                MAX_ATTEMPTS_REACHED,
                READY_NEXT_ATTEMPT_AT,
                null,
                null,
                NOW
        );

        List<WalletUpdatedOutboxEvent> events =
                walletUpdatedOutboxRepository.claimReadyEvents(CLAIM_LIMIT, MAX_ATTEMPTS, NOW, LOCKED_UNTIL);

        assertThat(events)
                .extracting(WalletUpdatedOutboxEvent::getTransactionId)
                .containsExactlyInAnyOrder(firstReadyTransactionId, secondReadyTransactionId);
        assertClaimed(firstReadyTransactionId, ONE_ATTEMPT);
        assertClaimed(secondReadyTransactionId, TWO_ATTEMPTS);
        assertStatusAndAttempts(thirdReadyTransactionId, OUTBOX_STATUS_PROCESSING, ONE_ATTEMPT);
        assertStatusAndAttempts(futureRetryTransactionId, OUTBOX_STATUS_FAILED, ONE_ATTEMPT);
        assertStatusAndAttempts(lockedTransactionId, OUTBOX_STATUS_PROCESSING, ONE_ATTEMPT);
        assertStatusAndAttempts(maxAttemptsReachedTransactionId, OUTBOX_STATUS_PENDING, MAX_ATTEMPTS_REACHED);
    }

    private void insertWallet() {
        jdbcTemplate.update(
                "insert into wallet (id, balance, updated) values (?, ?, ?)",
                WalletUpdatedOutboxRepositoryTests.WALLET_ID,
                WALLET_BALANCE,
                WALLET_UPDATED_AT
        );
    }

    private UUID insertOutboxEvent(
            WalletUpdatedOutboxStatus status,
            int attempts,
            OffsetDateTime nextAttemptAt,
            OffsetDateTime lockedUntil,
            String lastError,
            OffsetDateTime createdAt
    ) {
        UUID transactionId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                        insert into wallet_ledger (
                            transaction_id,
                            wallet_id,
                            type_operation,
                            amount,
                            status,
                            created_at,
                            balance_after
                        )
                        values (?, ?, ?::operation_type, ?, ?::operation_status, ?, ?)
                        """,
                transactionId,
                WalletUpdatedOutboxRepositoryTests.WALLET_ID,
                OPERATION_TYPE_CREDIT,
                EVENT_AMOUNT,
                OPERATION_STATUS_SUCCESS,
                createdAt,
                EVENT_BALANCE_AFTER
        );
        jdbcTemplate.update(
                """
                        insert into wallet_updated_outbox (
                            transaction_id,
                            wallet_id,
                            type_operation,
                            amount,
                            balance_after,
                            event_created_at,
                            status,
                            attempts,
                            next_attempt_at,
                            locked_until,
                            last_error,
                            created_at
                        )
                        values (?, ?, ?::operation_type, ?, ?, ?, ?::wallet_updated_outbox_status, ?, ?, ?, ?, ?)
                        """,
                transactionId,
                WalletUpdatedOutboxRepositoryTests.WALLET_ID,
                OPERATION_TYPE_CREDIT,
                EVENT_AMOUNT,
                EVENT_BALANCE_AFTER,
                createdAt,
                status.getValue(),
                attempts,
                nextAttemptAt,
                lockedUntil,
                lastError,
                createdAt
        );
        return transactionId;
    }

    private void assertClaimed(UUID transactionId, int attempts) {
        WalletUpdatedOutboxEvent outboxEvent = findOutboxEvent(transactionId);

        assertThat(outboxEvent).isNotNull();
        assertThat(ReflectionTestUtils.getField(outboxEvent, "status")).isEqualTo(OUTBOX_STATUS_PROCESSING);
        assertThat(outboxEvent.getAttempts()).isEqualTo(attempts);
        assertThat(ReflectionTestUtils.getField(outboxEvent, "lockedUntil")).isEqualTo(LOCKED_UNTIL);
        assertThat(ReflectionTestUtils.getField(outboxEvent, "lastError")).isNull();
    }

    private void assertStatusAndAttempts(UUID transactionId, WalletUpdatedOutboxStatus status, int attempts) {
        WalletUpdatedOutboxEvent outboxEvent = findOutboxEvent(transactionId);

        assertThat(outboxEvent).isNotNull();
        assertThat(ReflectionTestUtils.getField(outboxEvent, "status")).isEqualTo(status);
        assertThat(outboxEvent.getAttempts()).isEqualTo(attempts);
    }

    private WalletUpdatedOutboxEvent findOutboxEvent(UUID transactionId) {
        entityManager.clear();
        return entityManager.find(WalletUpdatedOutboxEvent.class, transactionId);
    }
}
