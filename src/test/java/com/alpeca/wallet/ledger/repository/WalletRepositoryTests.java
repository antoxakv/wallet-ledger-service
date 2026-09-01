package com.alpeca.wallet.ledger.repository;

import com.alpeca.wallet.ledger.entity.Wallet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WalletRepositoryTests extends PostgresRepositoryTestBase {

    private static final OffsetDateTime UPDATED_BOUNDARY = OffsetDateTime.parse("2026-08-31T10:00:00Z");

    private static final BigDecimal LOCKED_WALLET_BALANCE = BigDecimal.TEN;

    private static final UUID LOCKED_WALLET_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

    @Autowired
    private WalletRepository walletRepository;

    @Test
    void findByIdForUpdateLocksSelectedWalletRow() {
        insertWallet(LOCKED_WALLET_ID, LOCKED_WALLET_BALANCE, UPDATED_BOUNDARY);

        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transactionTemplate.executeWithoutResult(status -> {
            Optional<Wallet> wallet = walletRepository.findByIdForUpdate(LOCKED_WALLET_ID);

            assertThat(wallet).isPresent();
            assertThatThrownBy(() -> updateWalletFromSeparateConnection(LOCKED_WALLET_ID))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("canceling statement due to lock timeout");
        });
    }

    private void insertWallet(UUID walletId, BigDecimal balance, OffsetDateTime updated) {
        jdbcTemplate.update(
                "insert into wallet (id, balance, updated) values (?, ?, ?)",
                walletId,
                balance,
                updated
        );
    }

    private void updateWalletFromSeparateConnection(UUID walletId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement timeout = connection.prepareStatement("set lock_timeout = '200ms'");
             PreparedStatement update = connection.prepareStatement("update wallet set balance = balance + 1 where id = ?")) {
            timeout.execute();
            update.setObject(1, walletId);
            update.executeUpdate();
        }
    }
}
