package com.alpeca.wallet.ledger.service;

import com.alpeca.wallet.ledger.entity.WalletLedger;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Facade for wallet balance operations.
 * <p>
 * Coordinates wallet locking, transactional balance changes, duplicate transaction handling and
 * cache updates.
 */
public interface WalletFacadeService {

	/**
	 * Credits the wallet by the specified amount.
	 *
	 * @param walletId wallet identifier.
	 * @param transactionId unique transaction identifier used for idempotency.
	 * @param amount positive whole amount to add.
	 * @return ledger entry for the created or previously processed transaction.
	 */
	WalletLedger credit(UUID walletId, UUID transactionId, BigDecimal amount);

	/**
	 * Debits the wallet by the specified amount.
	 *
	 * @param walletId wallet identifier.
	 * @param transactionId unique transaction identifier used for idempotency.
	 * @param amount positive whole amount to subtract.
	 * @return ledger entry for the created or previously processed transaction.
	 */
	WalletLedger debit(UUID walletId, UUID transactionId, BigDecimal amount);

	/**
	 * Returns the current wallet balance.
	 *
	 * @param walletId wallet identifier.
	 * @return current wallet balance.
	 */
	BigDecimal getBalance(UUID walletId);
}
