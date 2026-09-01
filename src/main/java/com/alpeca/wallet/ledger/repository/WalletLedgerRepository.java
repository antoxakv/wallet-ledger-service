package com.alpeca.wallet.ledger.repository;

import com.alpeca.wallet.ledger.entity.WalletLedger;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Repository for immutable wallet ledger entries.
 */
public interface WalletLedgerRepository extends JpaRepository<WalletLedger, UUID> {
}
