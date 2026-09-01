package com.alpeca.wallet.ledger.grpc;

import com.alpeca.wallet.ledger.exception.InvalidWalletAmountException;
import com.alpeca.wallet.ledger.exception.InvalidUuidException;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Parses and validates primitive gRPC request fields.
 */
final class GrpcRequestParsers {

    private GrpcRequestParsers() {
    }

    static UUID parseUuid(String value, String fieldName) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            throw new InvalidUuidException(fieldName, value);
        }
    }

    static BigDecimal parseAmount(String amount) {
        try {
            return new BigDecimal(amount);
        } catch (NumberFormatException ex) {
            throw new InvalidWalletAmountException();
        }
    }
}
