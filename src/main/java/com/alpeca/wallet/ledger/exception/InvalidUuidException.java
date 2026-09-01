package com.alpeca.wallet.ledger.exception;

/**
 * Raised when a request field cannot be parsed as a UUID.
 */
public class InvalidUuidException extends RuntimeException {

	/**
	 * Creates an exception for an invalid UUID request field.
	 *
	 * @param fieldName request field name.
	 * @param value invalid field value.
	 */
	public InvalidUuidException(String fieldName, String value) {
		super("Invalid " + fieldName + ": " + value);
	}
}
