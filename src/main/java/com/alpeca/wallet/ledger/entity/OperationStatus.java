package com.alpeca.wallet.ledger.entity;

/**
 * Result status stored in the operation_status database enum.
 */
public enum OperationStatus {
	/**
	 * Operation changed the wallet balance successfully.
	 */
	SUCCESS("success"),

	/**
	 * Operation was recorded but did not change the wallet balance.
	 */
	FAILED("failed");

	private final String value;

	OperationStatus(String value) {
		this.value = value;
	}

	public String getValue() {
		return value;
	}

	public static OperationStatus fromValue(String value) {
		for (OperationStatus status : values()) {
			if (status.value.equals(value)) {
				return status;
			}
		}
		throw new IllegalArgumentException("Unknown operation status: " + value);
	}
}
