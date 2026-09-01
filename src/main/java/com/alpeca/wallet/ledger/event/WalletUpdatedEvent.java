package com.alpeca.wallet.ledger.event;

import com.alpeca.wallet.ledger.entity.OperationType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Event published after a successful wallet balance update.
 *
 * @param walletId wallet affected by the operation.
 * @param transactionId unique transaction identifier.
 * @param typeOperation wallet operation type.
 * @param amount positive operation amount in minor units.
 * @param createdAt timestamp when the wallet ledger entry was created.
 * @param balanceAfter wallet balance after the operation in minor units.
 */
public record WalletUpdatedEvent(
		UUID walletId,
		UUID transactionId,
		OperationType typeOperation,
		BigDecimal amount,
		OffsetDateTime createdAt,
		BigDecimal balanceAfter
) {
}
