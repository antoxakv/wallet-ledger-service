package com.alpeca.wallet.ledger.exception;

import java.util.UUID;

/**
 * Raised when an existing transaction id is reused with a different wallet operation payload.
 */
public class DuplicateTransactionConflictException extends RuntimeException {

	/**
	 * Creates an exception for the conflicting transaction id.
	 *
	 * @param transactionId transaction id that already belongs to another operation payload.
	 */
	public DuplicateTransactionConflictException(UUID transactionId) {
		super("Transaction id is already used for a different wallet operation: " + transactionId);
	}
}
