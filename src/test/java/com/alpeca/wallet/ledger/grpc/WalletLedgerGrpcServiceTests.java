package com.alpeca.wallet.ledger.grpc;

import com.alpeca.wallet.ledger.entity.OperationStatus;
import com.alpeca.wallet.ledger.entity.OperationType;
import com.alpeca.wallet.ledger.entity.WalletLedger;
import com.alpeca.wallet.ledger.exception.DuplicateTransactionConflictException;
import com.alpeca.wallet.ledger.exception.InvalidWalletAmountException;
import com.alpeca.wallet.ledger.exception.WalletLockedException;
import com.alpeca.wallet.ledger.exception.WalletNotFoundException;
import com.alpeca.wallet.ledger.grpc.v1.GetBalanceRequest;
import com.alpeca.wallet.ledger.grpc.v1.GetBalanceResponse;
import com.alpeca.wallet.ledger.grpc.v1.WalletLedgerServiceGrpc;
import com.alpeca.wallet.ledger.grpc.v1.WalletOperationRequest;
import com.alpeca.wallet.ledger.grpc.v1.WalletOperationResponse;
import com.alpeca.wallet.ledger.service.WalletFacadeService;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.grpc.server.autoconfigure.GrpcServerAutoConfiguration;
import org.springframework.boot.grpc.test.autoconfigure.AutoConfigureTestGrpcTransport;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.grpc.client.ChannelBuilderOptions;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@AutoConfigureTestGrpcTransport
@ImportAutoConfiguration(GrpcServerAutoConfiguration.class)
@SpringBootTest(classes = {
        WalletLedgerGrpcService.class,
        GrpcGlobalExceptionHandler.class
})
class WalletLedgerGrpcServiceTests {

    private static final String INVALID_UUID = "123";

    private static final String INVALID_AMOUNT = "abc";

    private static final BigDecimal CREDIT_AMOUNT = BigDecimal.valueOf(15);

    private static final BigDecimal CREDIT_BALANCE_AFTER = BigDecimal.valueOf(25);

    private static final BigDecimal DEBIT_AMOUNT = BigDecimal.valueOf(7);

    private static final BigDecimal DEBIT_BALANCE_AFTER = BigDecimal.valueOf(18);

    private static final BigDecimal FAILED_DEBIT_BALANCE_AFTER = BigDecimal.valueOf(5);

    private static final BigDecimal BALANCE_AMOUNT = BigDecimal.valueOf(42);

    private static final UUID WALLET_ID = UUID.randomUUID();

    private static final UUID TRANSACTION_ID = UUID.randomUUID();

    private static final WalletLedger CREDIT_LEDGER = new WalletLedger(
            TRANSACTION_ID,
            WALLET_ID,
            OperationType.CREDIT,
            CREDIT_AMOUNT,
            OperationStatus.SUCCESS,
            CREDIT_BALANCE_AFTER
    );

    private static final WalletLedger DEBIT_LEDGER = new WalletLedger(
            TRANSACTION_ID,
            WALLET_ID,
            OperationType.DEBIT,
            DEBIT_AMOUNT,
            OperationStatus.SUCCESS,
            DEBIT_BALANCE_AFTER
    );

    private static final WalletLedger FAILED_DEBIT_LEDGER = new WalletLedger(
            TRANSACTION_ID,
            WALLET_ID,
            OperationType.DEBIT,
            DEBIT_AMOUNT,
            OperationStatus.FAILED,
            FAILED_DEBIT_BALANCE_AFTER
    );

    private static final WalletOperationRequest CREDIT_REQUEST = walletOperationRequest(CREDIT_AMOUNT);

    private static final WalletOperationRequest DEBIT_REQUEST = walletOperationRequest(DEBIT_AMOUNT);

    private static final GetBalanceRequest GET_BALANCE_REQUEST = GetBalanceRequest.newBuilder()
            .setWalletId(WALLET_ID.toString())
            .build();

    @MockitoBean
    private WalletFacadeService walletFacadeService;

    private final WalletLedgerServiceGrpc.WalletLedgerServiceBlockingStub stub;

    @Autowired
    WalletLedgerGrpcServiceTests(GrpcChannelFactory channelFactory) {
        this.stub = WalletLedgerServiceGrpc.newBlockingStub(
                channelFactory.createChannel("test", ChannelBuilderOptions.defaults())
        );
    }

    @ParameterizedTest
    @MethodSource("invalidWalletOperationRequestData")
    void creditWithInvalidRequest(
            WalletOperationRequest request,
            String message
    ) {
        assertThatThrownBy(() -> stub.credit(request))
                .isInstanceOfSatisfying(
                        StatusRuntimeException.class,
                        ex -> {
                            Status exStatus = ex.getStatus();
                            assertThat(exStatus.getCode()).isEqualTo(Status.INVALID_ARGUMENT.getCode());
                            assertThat(exStatus.getDescription()).isEqualTo(message);
                        }
                );

        verifyNoInteractions(walletFacadeService);
    }

    @ParameterizedTest
    @MethodSource("walletOperationWithExceptionData")
    void creditWithException(Exception exception, Status.Code code, String message) {
        when(walletFacadeService.credit(WALLET_ID, TRANSACTION_ID, CREDIT_AMOUNT)).thenThrow(exception);

        assertThatThrownBy(() -> stub.credit(CREDIT_REQUEST))
                .isInstanceOfSatisfying(
                        StatusRuntimeException.class,
                        ex -> {
                            Status exStatus = ex.getStatus();
                            assertThat(exStatus.getCode()).isEqualTo(code);
                            assertThat(exStatus.getDescription()).isEqualTo(message);
                        }
                );

        verify(walletFacadeService).credit(WALLET_ID, TRANSACTION_ID, CREDIT_AMOUNT);
        verifyNoMoreInteractions(walletFacadeService);
    }

    @Test
    void creditWithSuccess() {
        when(walletFacadeService.credit(WALLET_ID, TRANSACTION_ID, CREDIT_AMOUNT)).thenReturn(CREDIT_LEDGER);

        WalletOperationResponse response = stub.credit(CREDIT_REQUEST);

        assertThat(response.getWalletId()).isEqualTo(WALLET_ID.toString());
        assertThat(response.getTransactionId()).isEqualTo(TRANSACTION_ID.toString());
        assertThat(response.getBalanceAfter()).isEqualTo(CREDIT_BALANCE_AFTER.toPlainString());
        assertThat(response.getStatus()).isEqualTo(OperationStatus.SUCCESS.getValue());

        verify(walletFacadeService).credit(WALLET_ID, TRANSACTION_ID, CREDIT_AMOUNT);
        verifyNoMoreInteractions(walletFacadeService);
    }

    @ParameterizedTest
    @MethodSource("invalidWalletOperationRequestData")
    void debitWithInvalidRequest(
            WalletOperationRequest request,
            String message
    ) {
        assertThatThrownBy(() -> stub.debit(request))
                .isInstanceOfSatisfying(
                        StatusRuntimeException.class,
                        ex -> {
                            Status exStatus = ex.getStatus();
                            assertThat(exStatus.getCode()).isEqualTo(Status.INVALID_ARGUMENT.getCode());
                            assertThat(exStatus.getDescription()).isEqualTo(message);
                        }
                );

        verifyNoInteractions(walletFacadeService);
    }

    private static Stream<Arguments> invalidWalletOperationRequestData() {
        return Stream.of(
                Arguments.of(
                        WalletOperationRequest.newBuilder(CREDIT_REQUEST)
                                .setWalletId(INVALID_UUID)
                                .build(),
                        "Invalid wallet id: " + INVALID_UUID
                ),
                Arguments.of(
                        WalletOperationRequest.newBuilder(CREDIT_REQUEST)
                                .setTransactionId(INVALID_UUID)
                                .build(),
                        "Invalid transaction id: " + INVALID_UUID
                ),
                Arguments.of(
                        WalletOperationRequest.newBuilder(CREDIT_REQUEST)
                                .setAmount(INVALID_AMOUNT)
                                .build(),
                        new InvalidWalletAmountException().getMessage()
                )
        );
    }

    @ParameterizedTest
    @MethodSource("walletOperationWithExceptionData")
    void debitWithException(Exception exception, Status.Code code, String message) {
        when(walletFacadeService.debit(WALLET_ID, TRANSACTION_ID, DEBIT_AMOUNT)).thenThrow(exception);

        assertThatThrownBy(() -> stub.debit(DEBIT_REQUEST))
                .isInstanceOfSatisfying(
                        StatusRuntimeException.class,
                        ex -> {
                            Status exStatus = ex.getStatus();
                            assertThat(exStatus.getCode()).isEqualTo(code);
                            assertThat(exStatus.getDescription()).isEqualTo(message);
                        }
                );

        verify(walletFacadeService).debit(WALLET_ID, TRANSACTION_ID, DEBIT_AMOUNT);
        verifyNoMoreInteractions(walletFacadeService);
    }

    private static Stream<Arguments> walletOperationWithExceptionData() {
        WalletLockedException walletLockedException = new WalletLockedException(WALLET_ID);
        WalletNotFoundException walletNotFoundException = new WalletNotFoundException(WALLET_ID);
        DuplicateTransactionConflictException duplicateTransactionConflictException =
                new DuplicateTransactionConflictException(TRANSACTION_ID);
        InvalidWalletAmountException invalidWalletAmountException = new InvalidWalletAmountException();
        return Stream.of(
                Arguments.of(walletLockedException, Status.ABORTED.getCode(), walletLockedException.getMessage()),
                Arguments.of(walletNotFoundException, Status.NOT_FOUND.getCode(), walletNotFoundException.getMessage()),
                Arguments.of(
                        duplicateTransactionConflictException,
                        Status.ALREADY_EXISTS.getCode(),
                        duplicateTransactionConflictException.getMessage()
                ),
                Arguments.of(
                        invalidWalletAmountException,
                        Status.INVALID_ARGUMENT.getCode(),
                        invalidWalletAmountException.getMessage()
                ),
                Arguments.of(new RuntimeException(), Status.INTERNAL.getCode(), "Internal server error")
        );
    }

    @ParameterizedTest
    @MethodSource("debitWithResponseData")
    void debitWithResponse(WalletLedger ledger, BigDecimal balanceAfter, OperationStatus status) {
        when(walletFacadeService.debit(WALLET_ID, TRANSACTION_ID, DEBIT_AMOUNT)).thenReturn(ledger);

        WalletOperationResponse response = stub.debit(DEBIT_REQUEST);

        assertThat(response.getWalletId()).isEqualTo(WALLET_ID.toString());
        assertThat(response.getTransactionId()).isEqualTo(TRANSACTION_ID.toString());
        assertThat(response.getBalanceAfter()).isEqualTo(balanceAfter.toPlainString());
        assertThat(response.getStatus()).isEqualTo(status.getValue());

        verify(walletFacadeService).debit(WALLET_ID, TRANSACTION_ID, DEBIT_AMOUNT);
        verifyNoMoreInteractions(walletFacadeService);
    }

    private static Stream<Arguments> debitWithResponseData() {
        return Stream.of(
                Arguments.of(DEBIT_LEDGER, DEBIT_BALANCE_AFTER, OperationStatus.SUCCESS),
                Arguments.of(FAILED_DEBIT_LEDGER, FAILED_DEBIT_BALANCE_AFTER, OperationStatus.FAILED)
        );
    }

    @Test
    void getBalanceWithInvalidWalletId() {
        assertThatThrownBy(() ->
                stub.getBalance(
                        GetBalanceRequest.newBuilder()
                                .setWalletId("123")
                                .build()
                )
        )
                .isInstanceOfSatisfying(
                        StatusRuntimeException.class,
                        ex -> {
                            Status exStatus = ex.getStatus();
                            assertThat(exStatus.getCode()).isEqualTo(Status.INVALID_ARGUMENT.getCode());
                            assertThat(exStatus.getDescription()).isEqualTo("Invalid wallet id: " + INVALID_UUID);
                        }
                );

        verifyNoInteractions(walletFacadeService);
    }

    @ParameterizedTest
    @MethodSource("getBalanceWithExceptionData")
    void getBalanceWithException(Exception exception, Status.Code code, String message) {
        when(walletFacadeService.getBalance(WALLET_ID)).thenThrow(exception);

        assertThatThrownBy(() -> stub.getBalance(GET_BALANCE_REQUEST))
                .isInstanceOfSatisfying(
                        StatusRuntimeException.class,
                        ex -> {
                            Status exStatus = ex.getStatus();
                            assertThat(exStatus.getCode()).isEqualTo(code);
                            assertThat(exStatus.getDescription()).isEqualTo(message);
                        }
                );

        verify(walletFacadeService).getBalance(WALLET_ID);
        verifyNoMoreInteractions(walletFacadeService);
    }

    private static Stream<Arguments> getBalanceWithExceptionData() {
        WalletLockedException walletLockedException = new WalletLockedException(WALLET_ID);
        WalletNotFoundException walletNotFoundException = new WalletNotFoundException(WALLET_ID);
        return Stream.of(
                Arguments.of(walletLockedException, Status.ABORTED.getCode(), walletLockedException.getMessage()),
                Arguments.of(walletNotFoundException, Status.NOT_FOUND.getCode(), walletNotFoundException.getMessage()),
                Arguments.of(new RuntimeException(), Status.INTERNAL.getCode(), "Internal server error")
        );
    }

    @Test
    void getBalanceWithSuccess() {
        when(walletFacadeService.getBalance(WALLET_ID)).thenReturn(BALANCE_AMOUNT);

        GetBalanceResponse response = stub.getBalance(GET_BALANCE_REQUEST);

        assertThat(response.getWalletId()).isEqualTo(WALLET_ID.toString());
        assertThat(response.getAmount()).isEqualTo(BALANCE_AMOUNT.toPlainString());

        verify(walletFacadeService).getBalance(WALLET_ID);
        verifyNoMoreInteractions(walletFacadeService);
    }

    private static WalletOperationRequest walletOperationRequest(BigDecimal amount) {
        return WalletOperationRequest.newBuilder()
                .setWalletId(WALLET_ID.toString())
                .setTransactionId(TRANSACTION_ID.toString())
                .setAmount(amount.toPlainString())
                .build();
    }
}
