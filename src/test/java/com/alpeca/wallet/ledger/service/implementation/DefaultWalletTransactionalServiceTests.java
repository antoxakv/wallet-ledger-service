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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultWalletTransactionalServiceTests {

    private static final UUID WALLET_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private static final UUID OTHER_WALLET_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private static final UUID TRANSACTION_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private static final BigDecimal BALANCE = BigDecimal.valueOf(100);

    private static final BigDecimal CREDIT_AMOUNT = BigDecimal.valueOf(15);

    private static final BigDecimal CREDIT_BALANCE_AFTER = BigDecimal.valueOf(115);

    private static final BigDecimal DEBIT_AMOUNT = BigDecimal.valueOf(20);

    private static final BigDecimal DEBIT_BALANCE_AFTER = BigDecimal.valueOf(80);

    private static final BigDecimal OTHER_AMOUNT = BigDecimal.valueOf(30);

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private WalletLedgerRepository walletLedgerRepository;

    @Mock
    private WalletUpdatedOutboxRepository walletUpdatedOutboxRepository;

    @InjectMocks
    private DefaultWalletTransactionalService service;

    @AfterEach
    void tearDown() {
        verifyNoMoreInteractions(walletRepository, walletLedgerRepository, walletUpdatedOutboxRepository);
    }

    @ParameterizedTest
    @MethodSource("successfulTransactionData")
    void walletOperationPersistsSuccessfulLedgerAndOutboxEvent(
            OperationType operationType,
            BigDecimal amount,
            BigDecimal balanceAfter
    ) {
        Wallet wallet = new Wallet(WALLET_ID, BALANCE);
        givenNewTransaction(wallet);

        WalletTransactionResult result = executeOperation(operationType, amount);

        assertThat(result.walletUpdated()).isTrue();
        assertThat(result.walletLedger().getTransactionId()).isEqualTo(TRANSACTION_ID);
        assertThat(result.walletLedger().getWalletId()).isEqualTo(WALLET_ID);
        assertThat(result.walletLedger().getTypeOperation()).isEqualTo(operationType);
        assertThat(result.walletLedger().getAmount()).isEqualTo(amount);
        assertThat(result.walletLedger().getStatus()).isEqualTo(OperationStatus.SUCCESS);
        assertThat(result.walletLedger().getBalanceAfter()).isEqualTo(balanceAfter);
        assertThat(wallet.getBalance()).isEqualTo(balanceAfter);

        verifySuccessfulOutboxEventSaved();
        verifyNewTransactionLoadedForUpdate();
    }

    private static Stream<Arguments> successfulTransactionData() {
        return Stream.of(
                Arguments.of(OperationType.CREDIT, CREDIT_AMOUNT, CREDIT_BALANCE_AFTER),
                Arguments.of(OperationType.DEBIT, DEBIT_AMOUNT, DEBIT_BALANCE_AFTER)
        );
    }

    @Test
    void debitPersistsFailedLedgerWhenBalanceIsInsufficient() {
        Wallet wallet = new Wallet(WALLET_ID, BigDecimal.TEN);
        givenNewTransaction(wallet);

        WalletTransactionResult result = service.debit(WALLET_ID, TRANSACTION_ID, DEBIT_AMOUNT);

        assertThat(result.walletUpdated()).isFalse();
        assertThat(result.walletLedger().getTransactionId()).isEqualTo(TRANSACTION_ID);
        assertThat(result.walletLedger().getWalletId()).isEqualTo(WALLET_ID);
        assertThat(result.walletLedger().getTypeOperation()).isEqualTo(OperationType.DEBIT);
        assertThat(result.walletLedger().getAmount()).isEqualTo(DEBIT_AMOUNT);
        assertThat(result.walletLedger().getStatus()).isEqualTo(OperationStatus.FAILED);
        assertThat(result.walletLedger().getBalanceAfter()).isEqualTo(BigDecimal.TEN);
        assertThat(wallet.getBalance()).isEqualTo(BigDecimal.TEN);
        verifyNewTransactionLoadedForUpdate();
        verifyNoInteractions(walletUpdatedOutboxRepository);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"0", "-1", "1.5"})
    void creditThrowsInvalidWalletAmountForInvalidAmount(String amount) {
        BigDecimal walletAmount = amount == null ? null : new BigDecimal(amount);

        assertThatThrownBy(() -> service.credit(WALLET_ID, TRANSACTION_ID, walletAmount))
                .isInstanceOf(InvalidWalletAmountException.class);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"0", "-1", "1.5"})
    void debitThrowsInvalidWalletAmountForInvalidAmount(String amount) {
        BigDecimal walletAmount = amount == null ? null : new BigDecimal(amount);

        assertThatThrownBy(() -> service.debit(WALLET_ID, TRANSACTION_ID, walletAmount))
                .isInstanceOf(InvalidWalletAmountException.class);
    }

    @ParameterizedTest
    @MethodSource("duplicateTransactionData")
    void walletOperationReturnsExistingLedgerForMatchingDuplicateTransaction(
            OperationType operationType,
            BigDecimal amount
    ) {
        WalletLedger ledger = walletLedger(operationType, amount);
        when(walletLedgerRepository.findById(TRANSACTION_ID)).thenReturn(Optional.of(ledger));

        WalletTransactionResult result = executeOperation(operationType, amount);

        assertThat(result.walletLedger()).isSameAs(ledger);
        assertThat(result.walletUpdated()).isFalse();
        verify(walletLedgerRepository).findById(TRANSACTION_ID);
    }

    private static Stream<Arguments> duplicateTransactionData() {
        return Stream.of(
                Arguments.of(OperationType.CREDIT, CREDIT_AMOUNT),
                Arguments.of(OperationType.DEBIT, DEBIT_AMOUNT)
        );
    }

    @ParameterizedTest
    @MethodSource("walletOperationData")
    void walletOperationThrowsWalletNotFoundWhenWalletDoesNotExist(OperationType operationType, BigDecimal amount) {
        when(walletLedgerRepository.findById(TRANSACTION_ID)).thenReturn(Optional.empty());
        when(walletRepository.findByIdForUpdate(WALLET_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> executeOperation(operationType, amount))
                .isInstanceOf(WalletNotFoundException.class)
                .hasMessageContaining(WALLET_ID.toString());

        verify(walletLedgerRepository).findById(TRANSACTION_ID);
        verify(walletRepository).findByIdForUpdate(WALLET_ID);
    }

    private static Stream<Arguments> walletOperationData() {
        return Stream.of(
                Arguments.of(OperationType.CREDIT, CREDIT_AMOUNT),
                Arguments.of(OperationType.DEBIT, DEBIT_AMOUNT)
        );
    }

    @Test
    void findDuplicateReturnsEmptyWhenTransactionDoesNotExist() {
        when(walletLedgerRepository.findById(TRANSACTION_ID)).thenReturn(Optional.empty());

        Optional<WalletLedger> ledger = service.findDuplicate(
                TRANSACTION_ID,
                WALLET_ID,
                OperationType.CREDIT,
                CREDIT_AMOUNT
        );

        assertThat(ledger).isEmpty();
        verify(walletLedgerRepository).findById(TRANSACTION_ID);
    }

    @ParameterizedTest
    @MethodSource("duplicateConflictData")
    void findDuplicateThrowsConflictWhenExistingLedgerDoesNotMatchRequest(
            WalletLedger ledger,
            OperationType operationType,
            BigDecimal amount
    ) {
        when(walletLedgerRepository.findById(TRANSACTION_ID)).thenReturn(Optional.of(ledger));

        assertThatThrownBy(() -> service.findDuplicate(TRANSACTION_ID, WALLET_ID, operationType, amount))
                .isInstanceOf(DuplicateTransactionConflictException.class)
                .hasMessageContaining(TRANSACTION_ID.toString());

        verify(walletLedgerRepository).findById(TRANSACTION_ID);
    }

    private static Stream<Arguments> duplicateConflictData() {
        return Stream.of(
                Arguments.of(
                        new WalletLedger(
                                TRANSACTION_ID,
                                OTHER_WALLET_ID,
                                OperationType.CREDIT,
                                CREDIT_AMOUNT,
                                OperationStatus.SUCCESS,
                                CREDIT_BALANCE_AFTER
                        ),
                        OperationType.CREDIT,
                        CREDIT_AMOUNT
                ),
                Arguments.of(walletLedger(OperationType.CREDIT, CREDIT_AMOUNT), OperationType.DEBIT, CREDIT_AMOUNT),
                Arguments.of(walletLedger(OperationType.CREDIT, CREDIT_AMOUNT), OperationType.CREDIT, OTHER_AMOUNT)
        );
    }

    @Test
    void findWalletReturnsWalletWhenWalletExists() {
        Wallet wallet = new Wallet(WALLET_ID, BALANCE);
        when(walletRepository.findById(WALLET_ID)).thenReturn(Optional.of(wallet));

        Wallet result = service.findWallet(WALLET_ID);

        assertThat(result).isSameAs(wallet);
        verify(walletRepository).findById(WALLET_ID);
    }

    @Test
    void findWalletThrowsWalletNotFoundWhenWalletDoesNotExist() {
        when(walletRepository.findById(WALLET_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findWallet(WALLET_ID))
                .isInstanceOf(WalletNotFoundException.class)
                .hasMessageContaining(WALLET_ID.toString());

        verify(walletRepository).findById(WALLET_ID);
    }

    private void givenNewTransaction(Wallet wallet) {
        when(walletLedgerRepository.findById(TRANSACTION_ID)).thenReturn(Optional.empty());
        when(walletRepository.findByIdForUpdate(WALLET_ID)).thenReturn(Optional.of(wallet));
        when(walletLedgerRepository.save(any(WalletLedger.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private void verifyNewTransactionLoadedForUpdate() {
        verify(walletLedgerRepository).findById(TRANSACTION_ID);
        verify(walletRepository).findByIdForUpdate(WALLET_ID);
        verify(walletLedgerRepository).save(any(WalletLedger.class));
    }

    private void verifySuccessfulOutboxEventSaved() {
        ArgumentCaptor<WalletUpdatedOutboxEvent> outboxCaptor = ArgumentCaptor.forClass(WalletUpdatedOutboxEvent.class);
        verify(walletUpdatedOutboxRepository).save(outboxCaptor.capture());
        assertThat(outboxCaptor.getValue().getTransactionId()).isEqualTo(TRANSACTION_ID);
        assertThat(outboxCaptor.getValue().getWalletId()).isEqualTo(WALLET_ID);
    }

    private WalletTransactionResult executeOperation(OperationType operationType, BigDecimal amount) {
        return switch (operationType) {
            case CREDIT -> service.credit(WALLET_ID, TRANSACTION_ID, amount);
            case DEBIT -> service.debit(WALLET_ID, TRANSACTION_ID, amount);
        };
    }

    private static WalletLedger walletLedger(OperationType operationType, BigDecimal amount) {
        BigDecimal balanceAfter = operationType == OperationType.CREDIT
                ? CREDIT_BALANCE_AFTER
                : DEBIT_BALANCE_AFTER;
        return new WalletLedger(
                TRANSACTION_ID,
                WALLET_ID,
                operationType,
                amount,
                OperationStatus.SUCCESS,
                balanceAfter
        );
    }
}
