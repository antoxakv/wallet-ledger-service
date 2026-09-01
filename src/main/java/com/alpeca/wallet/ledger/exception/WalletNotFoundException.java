package com.alpeca.wallet.ledger.exception;

import java.util.UUID;

/**
 * Raised when a wallet does not exist in persistent storage.
 */
public class WalletNotFoundException extends RuntimeException {

	/**
	 * Creates an exception for a missing wallet.
	 *
	 * @param walletId wallet id that was not found.
	 */
	public WalletNotFoundException(UUID walletId) {
		super("Wallet not found: " + walletId);
	}
}
