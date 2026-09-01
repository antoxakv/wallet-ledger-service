package com.alpeca.wallet.ledger.entity;

/**
 * Publication state stored in the wallet_updated_outbox_status database enum.
 */
public enum WalletUpdatedOutboxStatus {
	/**
	 * Event is ready for the first publication attempt.
	 */
	PENDING("pending"),

	/**
	 * Event has been claimed by a publisher instance.
	 */
	PROCESSING("processing"),

	/**
	 * Event was published to the message broker.
	 */
	PUBLISHED("published"),

	/**
	 * Event publication failed and may be retried later.
	 */
	FAILED("failed"),

	/**
	 * Event reached the maximum number of publication attempts.
	 */
	EXHAUSTED("exhausted");

	private final String value;

	WalletUpdatedOutboxStatus(String value) {
		this.value = value;
	}

	public String getValue() {
		return value;
	}

	public static WalletUpdatedOutboxStatus fromValue(String value) {
		for (WalletUpdatedOutboxStatus status : values()) {
			if (status.value.equals(value)) {
				return status;
			}
		}
		throw new IllegalArgumentException("Unknown wallet updated outbox status: " + value);
	}
}
