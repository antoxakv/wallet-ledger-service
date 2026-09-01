package com.alpeca.wallet.ledger.service;

import com.alpeca.wallet.ledger.entity.WalletUpdatedOutboxEvent;

/**
 * Publishes wallet update events claimed from the outbox.
 */
public interface WalletUpdatedEventPublisher {

	/**
	 * Publishes a wallet update event represented by an outbox record.
	 *
	 * @param event outbox event to publish.
	 */
	void publish(WalletUpdatedOutboxEvent event);
}
