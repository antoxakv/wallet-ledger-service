package com.alpeca.wallet.ledger.service;

import com.alpeca.wallet.ledger.dto.WalletTransactionResult;
import com.alpeca.wallet.ledger.entity.OperationType;
import com.alpeca.wallet.ledger.entity.Wallet;
import com.alpeca.wallet.ledger.entity.WalletLedger;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Transactional wallet operation boundary backed by the persistent ledger.
 */
public interface WalletTransactionalService {

	/**
	 * Applies a credit operation inside a database transaction.
	 *
	 * @param walletId wallet identifier.
	 * @param transactionId unique transaction identifier used for idempotency.
	 * @param amount positive whole amount to add.
	 * @return transaction result and whether the wallet balance changed.
	 */
	WalletTransactionResult credit(UUID walletId, UUID transactionId, BigDecimal amount);

	/**
	 * Applies a debit operation inside a database transaction.
	 *
	 * @param walletId wallet identifier.
	 * @param transactionId unique transaction identifier used for idempotency.
	 * @param amount positive whole amount to subtract.
	 * @return transaction result and whether the wallet balance changed.
	 */
	WalletTransactionResult debit(UUID walletId, UUID transactionId, BigDecimal amount);

	/**
	 * Finds a wallet by identifier.
	 *
	 * @param walletId wallet identifier.
	 * @return wallet entity.
	 */
	Wallet findWallet(UUID walletId);

	/**
	 * Finds an existing ledger entry for an idempotent retry and validates that it matches the request.
	 *
	 * @param transactionId transaction identifier to look up.
	 * @param walletId expected wallet identifier.
	 * @param operationType expected operation type.
	 * @param amount expected operation amount.
	 * @return matching ledger entry, or an empty result when the transaction was not found.
	 */
	Optional<WalletLedger> findDuplicate(UUID transactionId, UUID walletId, OperationType operationType, BigDecimal amount);
}
