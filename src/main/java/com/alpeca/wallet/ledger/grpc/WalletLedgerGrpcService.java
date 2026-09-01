package com.alpeca.wallet.ledger.grpc;

import com.alpeca.wallet.ledger.entity.WalletLedger;
import com.alpeca.wallet.ledger.grpc.v1.GetBalanceRequest;
import com.alpeca.wallet.ledger.grpc.v1.GetBalanceResponse;
import com.alpeca.wallet.ledger.grpc.v1.WalletLedgerServiceGrpc;
import com.alpeca.wallet.ledger.grpc.v1.WalletOperationRequest;
import com.alpeca.wallet.ledger.grpc.v1.WalletOperationResponse;
import com.alpeca.wallet.ledger.service.WalletFacadeService;
import io.grpc.stub.StreamObserver;
import org.springframework.grpc.server.service.GrpcService;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.function.Supplier;

import static com.alpeca.wallet.ledger.grpc.GrpcRequestFieldNames.TRANSACTION_ID;
import static com.alpeca.wallet.ledger.grpc.GrpcRequestFieldNames.WALLET_ID;
import static com.alpeca.wallet.ledger.grpc.GrpcRequestParsers.parseAmount;
import static com.alpeca.wallet.ledger.grpc.GrpcRequestParsers.parseUuid;

/**
 * gRPC adapter for wallet debit, credit, and balance operations.
 */
@GrpcService
class WalletLedgerGrpcService extends WalletLedgerServiceGrpc.WalletLedgerServiceImplBase {

    private final WalletFacadeService walletFacadeService;

    WalletLedgerGrpcService(WalletFacadeService walletFacadeService) {
        this.walletFacadeService = walletFacadeService;
    }

    @Override
    public void debit(WalletOperationRequest request, StreamObserver<WalletOperationResponse> responseObserver) {
        walletLedgerHandler(
                responseObserver,
                () -> walletFacadeService.debit(
                        parseUuid(request.getWalletId(), WALLET_ID),
                        parseUuid(request.getTransactionId(), TRANSACTION_ID),
                        parseAmount(request.getAmount())
                )
        );
    }

    @Override
    public void credit(WalletOperationRequest request, StreamObserver<WalletOperationResponse> responseObserver) {
        walletLedgerHandler(
                responseObserver,
                () -> walletFacadeService.credit(
                        parseUuid(request.getWalletId(), WALLET_ID),
                        parseUuid(request.getTransactionId(), TRANSACTION_ID),
                        parseAmount(request.getAmount())
                )
        );
    }

    @Override
    public void getBalance(GetBalanceRequest request, StreamObserver<GetBalanceResponse> responseObserver) {
        UUID walletId = parseUuid(request.getWalletId(), WALLET_ID);
        BigDecimal amount = walletFacadeService.getBalance(walletId);
        responseObserver.onNext(
                GetBalanceResponse.newBuilder()
                        .setWalletId(walletId.toString())
                        .setAmount(amount.toPlainString())
                        .build()
        );
        responseObserver.onCompleted();
    }

    private static void walletLedgerHandler(
            StreamObserver<WalletOperationResponse> responseObserver,
            Supplier<WalletLedger> supplier
    ) {
        WalletLedger ledger = supplier.get();
        responseObserver.onNext(
                WalletOperationResponse.newBuilder()
                        .setTransactionId(ledger.getTransactionId().toString())
                        .setWalletId(ledger.getWalletId().toString())
                        .setBalanceAfter(ledger.getBalanceAfter().toPlainString())
                        .setStatus(ledger.getStatus().getValue())
                        .build()
        );
        responseObserver.onCompleted();
    }
}
