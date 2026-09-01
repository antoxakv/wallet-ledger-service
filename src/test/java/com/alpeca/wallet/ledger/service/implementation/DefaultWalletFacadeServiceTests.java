package com.alpeca.wallet.ledger.service.implementation;

import com.alpeca.wallet.ledger.dto.WalletLock;
import com.alpeca.wallet.ledger.dto.WalletTransactionResult;
import com.alpeca.wallet.ledger.entity.OperationStatus;
import com.alpeca.wallet.ledger.entity.OperationType;
import com.alpeca.wallet.ledger.entity.Wallet;
import com.alpeca.wallet.ledger.entity.WalletLedger;
import com.alpeca.wallet.ledger.exception.WalletLockedException;
import com.alpeca.wallet.ledger.service.WalletLockService;
import com.alpeca.wallet.ledger.service.WalletTransactionalService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.QueryTimeoutException;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultWalletFacadeServiceTests {

    private static final UUID WALLET_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private static final UUID TRANSACTION_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private static final BigDecimal AMOUNT = BigDecimal.valueOf(10);

    private static final BigDecimal BALANCE = BigDecimal.valueOf(100);

    private static final BigDecimal BALANCE_AFTER = BigDecimal.valueOf(110);

    private static final WalletLock LOCK = new WalletLock("wallet:lock:" + WALLET_ID, "token");

    private static final RuntimeException OPERATION_EXCEPTION = new RuntimeException("failed");

    private static final QueryTimeoutException REDIS_EXCEPTION = new QueryTimeoutException("redis timed out");

    private static final DataIntegrityViolationException DUPLICATE_EXCEPTION =
            new DataIntegrityViolationException("duplicate");

    private static final Wallet WALLET = new Wallet(WALLET_ID, BALANCE);

    @Mock
    private WalletLockService walletLockService;

    @Mock
    private WalletTransactionalService walletTransactionalService;

    @InjectMocks
    private DefaultWalletFacadeService service;

    @AfterEach
    void tearDown() {
        verifyNoMoreInteractions(walletLockService, walletTransactionalService);
    }

    @ParameterizedTest
    @MethodSource("walletOperationData")
    void walletOperationReturnsLedgerAfterSuccessfulWalletUpdate(OperationType operationType) {
        WalletLedger ledger = walletLedger(operationType);
        when(walletLockService.tryLock(WALLET_ID)).thenReturn(Optional.of(LOCK));
        givenOperationReturns(operationType, new WalletTransactionResult(ledger, true));

        WalletLedger result = executeOperation(operationType);

        assertThat(result).isSameAs(ledger);
        verify(walletLockService).tryLock(WALLET_ID);
        verifyOperationExecuted(operationType);
        verify(walletLockService).unlock(LOCK);
    }

    @ParameterizedTest
    @MethodSource("walletOperationData")
    void walletOperationReturnsDuplicateLedgerWhenUniqueConstraintViolationMatchesRequest(OperationType operationType) {
        WalletLedger ledger = walletLedger(operationType);
        when(walletLockService.tryLock(WALLET_ID)).thenReturn(Optional.of(LOCK));
        givenOperationThrows(operationType, DUPLICATE_EXCEPTION);
        when(walletTransactionalService.findDuplicate(TRANSACTION_ID, WALLET_ID, operationType, AMOUNT))
                .thenReturn(Optional.of(ledger));

        WalletLedger result = executeOperation(operationType);

        assertThat(result).isSameAs(ledger);
        verify(walletLockService).tryLock(WALLET_ID);
        verifyOperationExecuted(operationType);
        verify(walletTransactionalService).findDuplicate(TRANSACTION_ID, WALLET_ID, operationType, AMOUNT);
        verify(walletLockService).unlock(LOCK);
    }

    @ParameterizedTest
    @MethodSource("walletOperationData")
    void walletOperationUnlocksWalletWhenOperationFails(OperationType operationType) {
        when(walletLockService.tryLock(WALLET_ID)).thenReturn(Optional.of(LOCK));
        givenOperationThrows(operationType, OPERATION_EXCEPTION);

        assertThatThrownBy(() -> executeOperation(operationType))
                .isSameAs(OPERATION_EXCEPTION);

        verify(walletLockService).tryLock(WALLET_ID);
        verifyOperationExecuted(operationType);
        verify(walletLockService).unlock(LOCK);
    }

    @ParameterizedTest
    @MethodSource("walletOperationData")
    void walletOperationThrowsWalletLockedWhenExclusiveLockIsNotAcquired(OperationType operationType) {
        when(walletLockService.tryLock(WALLET_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> executeOperation(operationType))
                .isInstanceOf(WalletLockedException.class)
                .hasMessageContaining(WALLET_ID.toString());

        verify(walletLockService).tryLock(WALLET_ID);
    }

    @ParameterizedTest
    @MethodSource("walletOperationData")
    void walletOperationUsesDatabaseWhenRedisLockAcquisitionFails(OperationType operationType) {
        WalletLedger ledger = walletLedger(operationType);
        when(walletLockService.tryLock(WALLET_ID)).thenThrow(REDIS_EXCEPTION);
        givenOperationReturns(operationType, new WalletTransactionResult(ledger, true));

        WalletLedger result = executeOperation(operationType);

        assertThat(result).isSameAs(ledger);
        verify(walletLockService).tryLock(WALLET_ID);
        verifyOperationExecuted(operationType);
    }

    @ParameterizedTest
    @MethodSource("walletOperationData")
    void walletOperationReturnsLedgerWhenRedisUnlockFails(OperationType operationType) {
        WalletLedger ledger = walletLedger(operationType);
        when(walletLockService.tryLock(WALLET_ID)).thenReturn(Optional.of(LOCK));
        givenOperationReturns(operationType, new WalletTransactionResult(ledger, false));
        doThrow(REDIS_EXCEPTION).when(walletLockService).unlock(LOCK);

        WalletLedger result = executeOperation(operationType);

        assertThat(result).isSameAs(ledger);
        verify(walletLockService).tryLock(WALLET_ID);
        verifyOperationExecuted(operationType);
        verify(walletLockService).unlock(LOCK);
    }

    private static Stream<Arguments> walletOperationData() {
        return Stream.of(
                Arguments.of(OperationType.CREDIT),
                Arguments.of(OperationType.DEBIT)
        );
    }

    @Test
    void getBalanceReturnsDatabaseBalance() {
        when(walletTransactionalService.findWallet(WALLET_ID)).thenReturn(WALLET);

        BigDecimal balance = service.getBalance(WALLET_ID);

        assertThat(balance).isEqualTo(BALANCE);
        verify(walletTransactionalService).findWallet(WALLET_ID);
    }

    @Test
    void getBalancePropagatesDatabaseException() {
        RuntimeException exception = new RuntimeException("database down");
        when(walletTransactionalService.findWallet(WALLET_ID)).thenThrow(exception);

        assertThatThrownBy(() -> service.getBalance(WALLET_ID))
                .isSameAs(exception);

        verify(walletTransactionalService).findWallet(WALLET_ID);
    }

    private void givenOperationReturns(OperationType operationType, WalletTransactionResult result) {
        switch (operationType) {
            case CREDIT -> when(walletTransactionalService.credit(WALLET_ID, TRANSACTION_ID, AMOUNT))
                    .thenReturn(result);
            case DEBIT -> when(walletTransactionalService.debit(WALLET_ID, TRANSACTION_ID, AMOUNT))
                    .thenReturn(result);
        }
    }

    private void givenOperationThrows(OperationType operationType, RuntimeException exception) {
        switch (operationType) {
            case CREDIT -> when(walletTransactionalService.credit(WALLET_ID, TRANSACTION_ID, AMOUNT))
                    .thenThrow(exception);
            case DEBIT -> when(walletTransactionalService.debit(WALLET_ID, TRANSACTION_ID, AMOUNT))
                    .thenThrow(exception);
        }
    }

    private void verifyOperationExecuted(OperationType operationType) {
        switch (operationType) {
            case CREDIT -> verify(walletTransactionalService).credit(WALLET_ID, TRANSACTION_ID, AMOUNT);
            case DEBIT -> verify(walletTransactionalService).debit(WALLET_ID, TRANSACTION_ID, AMOUNT);
        }
    }

    private WalletLedger executeOperation(OperationType operationType) {
        return switch (operationType) {
            case CREDIT -> service.credit(WALLET_ID, TRANSACTION_ID, AMOUNT);
            case DEBIT -> service.debit(WALLET_ID, TRANSACTION_ID, AMOUNT);
        };
    }

    private static WalletLedger walletLedger(OperationType operationType) {
        return new WalletLedger(
                TRANSACTION_ID,
                WALLET_ID,
                operationType,
                AMOUNT,
                OperationStatus.SUCCESS,
                BALANCE_AFTER
        );
    }
}
