package com.alpeca.wallet.ledger.grpc;


import com.alpeca.wallet.ledger.exception.DuplicateTransactionConflictException;
import com.alpeca.wallet.ledger.exception.InvalidUuidException;
import com.alpeca.wallet.ledger.exception.InvalidWalletAmountException;
import com.alpeca.wallet.ledger.exception.WalletLockedException;
import com.alpeca.wallet.ledger.exception.WalletNotFoundException;
import io.grpc.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.grpc.server.advice.GrpcAdvice;
import org.springframework.grpc.server.advice.GrpcExceptionHandler;

/**
 * Maps domain and validation exceptions to gRPC statuses.
 */
@GrpcAdvice
public class GrpcGlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GrpcGlobalExceptionHandler.class);

    @GrpcExceptionHandler(WalletNotFoundException.class)
    public Status handleWalletNotFound(WalletNotFoundException ex) {
        log.warn(ex.getMessage());
        return Status.NOT_FOUND.withDescription(ex.getMessage());
    }

    @GrpcExceptionHandler(value = {
            InvalidUuidException.class,
            InvalidWalletAmountException.class
    })
    public Status handleInvalidUuid(RuntimeException ex) {
        log.warn(ex.getMessage());
        return Status.INVALID_ARGUMENT.withDescription(ex.getMessage());
    }

    @GrpcExceptionHandler(WalletLockedException.class)
    public Status handleWalletLocked(WalletLockedException ex) {
        log.warn(ex.getMessage());
        return Status.ABORTED.withDescription(ex.getMessage());
    }

    @GrpcExceptionHandler(DuplicateTransactionConflictException.class)
    public Status handleDuplicateTransactionConflict(DuplicateTransactionConflictException ex) {
        log.warn(ex.getMessage());
        return Status.ALREADY_EXISTS.withDescription(ex.getMessage());
    }

    @GrpcExceptionHandler(Exception.class)
    public Status handleUnexpected(Exception ex) {
        log.error("Unexpected exception", ex);
        return Status.INTERNAL.withDescription("Internal server error");
    }
}
