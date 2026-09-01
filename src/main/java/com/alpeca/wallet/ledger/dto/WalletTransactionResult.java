package com.alpeca.wallet.ledger.dto;

import com.alpeca.wallet.ledger.entity.WalletLedger;

/**
 * Result of a wallet transaction and whether it changed the wallet balance.
 *
 * @param walletLedger ledger entry returned for the request.
 * @param walletUpdated true when the request changed the wallet balance.
 */
public record WalletTransactionResult(
		WalletLedger walletLedger,
		boolean walletUpdated
) {
}
