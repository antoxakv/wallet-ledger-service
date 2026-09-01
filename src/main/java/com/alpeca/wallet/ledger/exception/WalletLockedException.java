package com.alpeca.wallet.ledger.exception;

import java.util.UUID;

/**
 * Raised when a wallet operation cannot acquire the required Redis lock.
 */
public class WalletLockedException extends RuntimeException {

	/**
	 * Creates an exception for a locked wallet.
	 *
	 * @param walletId wallet id whose lock could not be acquired.
	 */
	public WalletLockedException(UUID walletId) {
		super("Wallet is locked: " + walletId);
	}
}
