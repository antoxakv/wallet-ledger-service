package com.alpeca.wallet.ledger.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Current wallet balance state.
 */
@Entity
@Table(name = "wallet")
public class Wallet {

    /**
     * Wallet identifier.
     */
    @Id
    @Column(nullable = false)
    private UUID id;

    /**
     * Current wallet balance in minor units. Must not be negative.
     */
    @Column(nullable = false, precision = 38)
    private BigDecimal balance;

    /**
     * Timestamp when the wallet balance was last updated.
     */
    @UpdateTimestamp
    @Column(nullable = false)
    private OffsetDateTime updated;

    protected Wallet() {
    }

    public Wallet(UUID id, BigDecimal balance) {
        this.id = id;
        this.balance = balance;
    }

    public UUID getId() {
        return id;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }
}
