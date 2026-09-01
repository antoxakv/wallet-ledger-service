package com.alpeca.wallet.ledger.service;

import com.alpeca.wallet.ledger.dto.WalletLock;

import java.util.Optional;
import java.util.UUID;

/**
 * Provides short-lived wallet locks used to serialize balance-changing operations and cache reads.
 */
public interface WalletLockService {

	/**
	 * Attempts to acquire an exclusive lock for a wallet.
	 *
	 * @param walletId wallet identifier.
	 * @return acquired lock ownership data, or an empty result when the lock could not be obtained.
	 */
	Optional<WalletLock> tryLock(UUID walletId);

	/**
	 * Releases a previously acquired wallet lock.
	 *
	 * @param walletLock lock ownership data returned by a lock acquisition method.
	 */
	void unlock(WalletLock walletLock);
}
