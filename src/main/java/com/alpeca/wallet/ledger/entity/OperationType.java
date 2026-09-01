package com.alpeca.wallet.ledger.entity;

/**
 * Wallet movement type stored in the operation_type database enum.
 */
public enum OperationType {
	/**
	 * Subtracts funds from a wallet.
	 */
	DEBIT("debit"),

	/**
	 * Adds funds to a wallet.
	 */
	CREDIT("credit");

	private final String value;

	OperationType(String value) {
		this.value = value;
	}

	public String getValue() {
		return value;
	}

	public static OperationType fromValue(String value) {
		for (OperationType type : values()) {
			if (type.value.equals(value)) {
				return type;
			}
		}
		throw new IllegalArgumentException("Unknown operation type: " + value);
	}
}
