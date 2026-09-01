package com.alpeca.wallet.ledger.grpc;

/**
 * Shared gRPC request field names used in validation errors.
 */
final class GrpcRequestFieldNames {

    static final String WALLET_ID = "wallet id";
    static final String TRANSACTION_ID = "transaction id";

    private GrpcRequestFieldNames() {
    }
}
