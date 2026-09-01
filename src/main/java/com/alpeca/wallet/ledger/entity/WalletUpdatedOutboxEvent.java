package com.alpeca.wallet.ledger.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnTransformer;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Outbox for reliable WalletUpdatedEvent publication.
 */
@Entity
@Table(name = "wallet_updated_outbox")
public class WalletUpdatedOutboxEvent {

	/**
	 * Transaction identifier and outbox event identifier.
	 */
	@Id
	@Column(name = "transaction_id", nullable = false)
	private UUID transactionId;

	/**
	 * Wallet affected by the published event.
	 */
	@Column(name = "wallet_id", nullable = false)
	private UUID walletId;

	/**
	 * Wallet operation type copied from the ledger entry.
	 */
	@Column(name = "type_operation", nullable = false, columnDefinition = "operation_type")
	@ColumnTransformer(write = "?::operation_type")
	private OperationType typeOperation;

	/**
	 * Positive operation amount in minor units copied from the ledger entry.
	 */
	@Column(nullable = false, precision = 38)
	private BigDecimal amount;

	/**
	 * Wallet balance after the operation in minor units.
	 */
	@Column(name = "balance_after", nullable = false, precision = 38)
	private BigDecimal balanceAfter;

	/**
	 * Business event creation timestamp copied from the ledger entry.
	 */
	@Column(name = "event_created_at", nullable = false)
	private OffsetDateTime eventCreatedAt;

	/**
	 * Outbox publication status.
	 */
	@Column(nullable = false, columnDefinition = "wallet_updated_outbox_status")
	@ColumnTransformer(write = "?::wallet_updated_outbox_status")
	private WalletUpdatedOutboxStatus status;

	/**
	 * Number of broker publication attempts.
	 */
	@Column(nullable = false)
	private int attempts;

	/**
	 * Earliest timestamp when the publisher may retry the event.
	 */
	@Column(name = "next_attempt_at", nullable = false)
	private OffsetDateTime nextAttemptAt;

	/**
	 * Timestamp until which a publisher instance owns this event.
	 */
	@Column(name = "locked_until")
	private OffsetDateTime lockedUntil;

	/**
	 * Last broker publication error message.
	 */
	@Column(name = "last_error")
	private String lastError;

	/**
	 * Timestamp when the event was successfully published.
	 */
	@Column(name = "published_at")
	private OffsetDateTime publishedAt;

	protected WalletUpdatedOutboxEvent() {
	}

	public WalletUpdatedOutboxEvent(WalletLedger ledger) {
		this.transactionId = ledger.getTransactionId();
		this.walletId = ledger.getWalletId();
		this.typeOperation = ledger.getTypeOperation();
		this.amount = ledger.getAmount();
		this.balanceAfter = ledger.getBalanceAfter();
		this.eventCreatedAt = ledger.createdAt();
		this.status = WalletUpdatedOutboxStatus.PENDING;
		this.attempts = 0;
		this.nextAttemptAt = OffsetDateTime.now();
	}

	public UUID getTransactionId() {
		return transactionId;
	}

	public UUID getWalletId() {
		return walletId;
	}

	public OperationType getTypeOperation() {
		return typeOperation;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public BigDecimal getBalanceAfter() {
		return balanceAfter;
	}

	public OffsetDateTime getEventCreatedAt() {
		return eventCreatedAt;
	}

	public int getAttempts() {
		return attempts;
	}

	public void markPublished(OffsetDateTime publishedAt) {
		this.status = WalletUpdatedOutboxStatus.PUBLISHED;
		this.lockedUntil = null;
		this.lastError = null;
		this.publishedAt = publishedAt;
	}

	public void markFailed(RuntimeException exception, OffsetDateTime now, java.time.Duration retryDelay) {
		this.status = WalletUpdatedOutboxStatus.FAILED;
		this.nextAttemptAt = now.plus(retryDelay);
		this.lockedUntil = null;
		this.lastError = exception.getMessage();
	}

	public void markExhausted(RuntimeException exception) {
		this.status = WalletUpdatedOutboxStatus.EXHAUSTED;
		this.lockedUntil = null;
		this.lastError = exception.getMessage();
	}
}
