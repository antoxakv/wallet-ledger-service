package com.alpeca.wallet.ledger.service.implementation;

import com.alpeca.wallet.ledger.dto.WalletTransactionResult;
import com.alpeca.wallet.ledger.entity.OperationStatus;
import com.alpeca.wallet.ledger.entity.OperationType;
import com.alpeca.wallet.ledger.entity.Wallet;
import com.alpeca.wallet.ledger.entity.WalletLedger;
import com.alpeca.wallet.ledger.entity.WalletUpdatedOutboxEvent;
import com.alpeca.wallet.ledger.exception.DuplicateTransactionConflictException;
import com.alpeca.wallet.ledger.exception.InvalidWalletAmountException;
import com.alpeca.wallet.ledger.exception.WalletNotFoundException;
import com.alpeca.wallet.ledger.repository.WalletLedgerRepository;
import com.alpeca.wallet.ledger.repository.WalletRepository;
import com.alpeca.wallet.ledger.repository.WalletUpdatedOutboxRepository;
import com.alpeca.wallet.ledger.service.WalletTransactionalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Default transactional wallet service.
 * <p>
 * Validates operation amounts, detects idempotent retries, persists ledger entries and writes
 * wallet update events to the outbox in the same database transaction.
 */
@Service
class DefaultWalletTransactionalService implements WalletTransactionalService {

    private static final Logger log = LoggerFactory.getLogger(DefaultWalletTransactionalService.class);

    private final WalletRepository walletRepository;

    private final WalletLedgerRepository walletLedgerRepository;

    private final WalletUpdatedOutboxRepository walletUpdatedOutboxRepository;

    DefaultWalletTransactionalService(
            WalletRepository walletRepository,
            WalletLedgerRepository walletLedgerRepository,
            WalletUpdatedOutboxRepository walletUpdatedOutboxRepository
    ) {
        this.walletRepository = walletRepository;
        this.walletLedgerRepository = walletLedgerRepository;
        this.walletUpdatedOutboxRepository = walletUpdatedOutboxRepository;
    }

    @Override
    @Transactional
    public WalletTransactionResult credit(UUID walletId, UUID transactionId, BigDecimal amount) {
        return validateAndFindDuplicateResult(walletId, transactionId, OperationType.CREDIT, amount)
                .orElseGet(
                        () -> {
                            Wallet wallet = findWalletForUpdate(walletId);
                            BigDecimal balanceAfter = wallet.getBalance().add(amount);
                            wallet.setBalance(balanceAfter);

                            return successfulTransactionResult(
                                    transactionId,
                                    walletId,
                                    OperationType.CREDIT,
                                    amount,
                                    balanceAfter
                            );
                        }
                );
    }

    @Override
    @Transactional
    public WalletTransactionResult debit(UUID walletId, UUID transactionId, BigDecimal amount) {
        return validateAndFindDuplicateResult(walletId, transactionId, OperationType.DEBIT, amount)
                .orElseGet(
                        () -> {
                            Wallet wallet = findWalletForUpdate(walletId);
                            BigDecimal currentAmount = wallet.getBalance();

                            if (currentAmount.compareTo(amount) < 0) {
                                WalletLedger ledger = walletLedgerRepository.save(
                                        new WalletLedger(
                                                transactionId,
                                                walletId,
                                                OperationType.DEBIT,
                                                amount,
                                                OperationStatus.FAILED,
                                                currentAmount
                                        )
                                );
                                return new WalletTransactionResult(ledger, false);
                            }

                            BigDecimal balanceAfter = currentAmount.subtract(amount);
                            wallet.setBalance(balanceAfter);

                            return successfulTransactionResult(
                                    transactionId,
                                    walletId,
                                    OperationType.DEBIT,
                                    amount,
                                    balanceAfter
                            );
                        }
                );
    }

    @Override
    @Transactional(readOnly = true)
    public Wallet findWallet(UUID walletId) {
        return walletRepository.findById(walletId)
                .orElseThrow(() -> new WalletNotFoundException(walletId));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<WalletLedger> findDuplicate(
            UUID transactionId,
            UUID walletId,
            OperationType operationType,
            BigDecimal amount
    ) {
        return walletLedgerRepository.findById(transactionId)
                .map(ledger -> {
                    validateDuplicateMatchesRequest(ledger, walletId, operationType, amount);
                    return ledger;
                });
    }

    private Optional<WalletTransactionResult> validateAndFindDuplicateResult(
            UUID walletId,
            UUID transactionId,
            OperationType operationType,
            BigDecimal amount
    ) {
        if (amount == null || amount.scale() > 0 || amount.signum() <= 0) {
            throw new InvalidWalletAmountException();
        }

        return walletLedgerRepository.findById(transactionId)
                .map(ledger -> {
                    validateDuplicateMatchesRequest(ledger, walletId, operationType, amount);
                    log.info(
                            "Returning existing wallet operation result for duplicate transaction: transactionId={}, walletId={}, typeOperation={}, status={}",
                            ledger.getTransactionId(),
                            ledger.getWalletId(),
                            ledger.getTypeOperation(),
                            ledger.getStatus()
                    );
                    return new WalletTransactionResult(ledger, false);
                });
    }

    /**
     * Stores a successful ledger entry and creates the corresponding outbox event.
     */
    private WalletTransactionResult successfulTransactionResult(
            UUID transactionId,
            UUID walletId,
            OperationType operationType,
            BigDecimal amount,
            BigDecimal balanceAfter
    ) {
        WalletLedger ledger = walletLedgerRepository.save(
                new WalletLedger(
                        transactionId,
                        walletId,
                        operationType,
                        amount,
                        OperationStatus.SUCCESS,
                        balanceAfter
                )
        );
        walletUpdatedOutboxRepository.save(new WalletUpdatedOutboxEvent(ledger));
        return new WalletTransactionResult(ledger, true);
    }

    /**
     * Finds a wallet using a row-level database lock for balance mutation.
     */
    private Wallet findWalletForUpdate(UUID walletId) {
        return walletRepository.findByIdForUpdate(walletId)
                .orElseThrow(() -> new WalletNotFoundException(walletId));
    }

    /**
     * Ensures that an existing transaction identifier belongs to the same wallet operation request.
     */
    private static void validateDuplicateMatchesRequest(
            WalletLedger ledger,
            UUID walletId,
            OperationType operationType,
            BigDecimal amount
    ) {
        if (!ledger.getWalletId().equals(walletId)
                || ledger.getTypeOperation() != operationType
                || ledger.getAmount().compareTo(amount) != 0) {
            throw new DuplicateTransactionConflictException(ledger.getTransactionId());
        }
    }

}
