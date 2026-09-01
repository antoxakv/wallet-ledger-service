package com.alpeca.wallet.ledger.service.implementation;

import com.alpeca.wallet.ledger.dto.WalletLock;
import com.alpeca.wallet.ledger.dto.WalletTransactionResult;
import com.alpeca.wallet.ledger.entity.OperationType;
import com.alpeca.wallet.ledger.entity.WalletLedger;
import com.alpeca.wallet.ledger.exception.WalletLockedException;
import com.alpeca.wallet.ledger.service.WalletFacadeService;
import com.alpeca.wallet.ledger.service.WalletLockService;
import com.alpeca.wallet.ledger.service.WalletTransactionalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Default wallet facade implementation.
 * <p>
 * Uses Redis locks when available, delegates balance operations to the transactional service and
 * falls back to database locking when Redis is unavailable.
 */
@Service
class DefaultWalletFacadeService implements WalletFacadeService {

    private static final Logger log = LoggerFactory.getLogger(DefaultWalletFacadeService.class);

    private final WalletLockService walletLockService;

    private final WalletTransactionalService walletTransactionalService;

    DefaultWalletFacadeService(
            WalletLockService walletLockService,
            WalletTransactionalService walletTransactionalService
    ) {
        this.walletLockService = walletLockService;
        this.walletTransactionalService = walletTransactionalService;
    }

    @Override
    public WalletLedger credit(UUID walletId, UUID transactionId, BigDecimal amount) {
        return withWalletLock(
                walletId,
                transactionId,
                OperationType.CREDIT,
                amount,
                () -> walletTransactionalService.credit(walletId, transactionId, amount)
        );
    }

    @Override
    public WalletLedger debit(UUID walletId, UUID transactionId, BigDecimal amount) {
        return withWalletLock(
                walletId,
                transactionId,
                OperationType.DEBIT,
                amount,
                () -> walletTransactionalService.debit(walletId, transactionId, amount)
        );
    }

    @Override
    public BigDecimal getBalance(UUID walletId) {
        return walletTransactionalService.findWallet(walletId).getBalance();
    }

    /**
     * Executes a balance-changing operation under an exclusive wallet lock.
     * <p>
     * If Redis is unavailable, the operation is executed through the transactional service and
     * relies on database locking. If a database unique constraint reports a duplicate transaction,
     * the existing matching ledger entry is returned to preserve idempotency.
     */
    private WalletLedger withWalletLock(
            UUID walletId,
            UUID transactionId,
            OperationType operationType,
            BigDecimal amount,
            Supplier<WalletTransactionResult> operation
    ) {
        WalletLock lock = null;
        try {
            lock = walletLockService.tryLock(walletId)
                    .orElseThrow(() -> new WalletLockedException(walletId));
        } catch (DataAccessException ex) {
            log.warn("Wallet lock acquisition failed, falling back to database locking: walletId={}", walletId, ex);
        }
        try {
            WalletLedger ledger;
            try {
                WalletTransactionResult transactionResult = operation.get();
                ledger = transactionResult.walletLedger();
            } catch (DataIntegrityViolationException ex) {
                ledger = walletTransactionalService.findDuplicate(
                                transactionId,
                                walletId,
                                operationType,
                                amount
                        )
                        .orElseThrow(() -> ex);
            }
            return ledger;
        } finally {
            if (lock != null) {
                unlock(lock);
            }
        }
    }

    private void unlock(WalletLock lock) {
        try {
            walletLockService.unlock(lock);
        } catch (DataAccessException ex) {
            log.warn("Wallet lock release failed and will expire by TTL: lockKey={}", lock.key(), ex);
        }
    }
}
