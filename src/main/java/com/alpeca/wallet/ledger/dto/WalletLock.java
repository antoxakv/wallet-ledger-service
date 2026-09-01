package com.alpeca.wallet.ledger.dto;

/**
 * Redis wallet lock ownership data.
 *
 * @param key Redis key used for the lock.
 * @param token Lock ownership token required to release the lock safely.
 */
public record WalletLock(
		String key,
		String token
) {
}
