package com.alpeca.wallet.ledger.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.hibernate.annotations.ColumnTransformer;
import org.springframework.data.domain.Persistable;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Immutable append-only wallet transaction log.
 */
@Entity
@Table(name = "wallet_ledger")
public class WalletLedger implements Persistable<UUID> {

	/**
	 * Unique transaction identifier used for idempotency.
	 */
	@Id
	@Column(name = "transaction_id", nullable = false)
	private UUID transactionId;

	/**
	 * Wallet affected by the transaction.
	 */
	@Column(name = "wallet_id", nullable = false)
	private UUID walletId;

	/**
	 * Wallet operation type: debit or credit.
	 */
	@Column(name = "type_operation", nullable = false, columnDefinition = "operation_type")
	@ColumnTransformer(write = "?::operation_type")
	private OperationType typeOperation;

	/**
	 * Positive operation amount in minor units.
	 */
	@Column(nullable = false, precision = 38)
	private BigDecimal amount;

	/**
	 * Operation result status.
	 */
	@Column(nullable = false, columnDefinition = "operation_status")
	@ColumnTransformer(write = "?::operation_status")
	private OperationStatus status;

	/**
	 * Timestamp when the ledger entry was created.
	 */
	@Column(name = "created_at", nullable = false, updatable = false)
	private OffsetDateTime createdAt;

	/**
	 * Wallet balance after applying the operation in minor units.
	 */
	@Column(name = "balance_after", nullable = false, precision = 38)
	private BigDecimal balanceAfter;

	protected WalletLedger() {
	}

	public WalletLedger(UUID transactionId, UUID walletId, OperationType typeOperation, BigDecimal amount, OperationStatus status, BigDecimal balanceAfter) {
		this.transactionId = transactionId;
		this.walletId = walletId;
		this.typeOperation = typeOperation;
		this.amount = amount;
		this.status = status;
		this.createdAt = OffsetDateTime.now();
		this.balanceAfter = balanceAfter;
	}

	public UUID getTransactionId() {
		return transactionId;
	}

	@Override
	public UUID getId() {
		return transactionId;
	}

	@Override
	@Transient
	public boolean isNew() {
		return true;
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

	public OperationStatus getStatus() {
		return status;
	}

	OffsetDateTime createdAt() {
		return createdAt;
	}

	public BigDecimal getBalanceAfter() {
		return balanceAfter;
	}
}
