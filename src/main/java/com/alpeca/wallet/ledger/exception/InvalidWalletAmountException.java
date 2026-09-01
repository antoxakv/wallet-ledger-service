package com.alpeca.wallet.ledger.exception;

/**
 * Raised when a wallet operation amount is missing, non-positive, or not an integer value.
 */
public class InvalidWalletAmountException extends RuntimeException {

	/**
	 * Creates an exception for an invalid wallet operation amount.
	 */
	public InvalidWalletAmountException() {
		super("Amount must be a positive integer value");
	}
}
