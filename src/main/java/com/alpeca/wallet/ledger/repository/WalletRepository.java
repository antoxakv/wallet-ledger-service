package com.alpeca.wallet.ledger.repository;

import com.alpeca.wallet.ledger.entity.Wallet;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for persistent wallet balance state.
 */
public interface WalletRepository extends JpaRepository<Wallet, UUID> {

	/**
	 * Finds a wallet and locks the row for balance mutation.
	 *
	 * @param walletId wallet identifier.
	 * @return locked wallet when it exists.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select w from Wallet w where w.id = :walletId")
	Optional<Wallet> findByIdForUpdate(UUID walletId);
}
