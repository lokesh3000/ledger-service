package com.ledger.account;

import com.ledger.common.InsufficientFundsException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * An account holds a cached balance in minor units (cents).
 * The ledger entries are the source of truth; the balance column is kept in
 * step with them under a row lock and verified by reconciliation (Phase 2).
 */
@Entity
@Table(name = "accounts")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountType type;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false)
    private long balance;

    @Column(name = "allow_negative", nullable = false)
    private boolean allowNegative;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Account() {
        // required by JPA
    }

    public Account(String name, AccountType type, String currency, boolean allowNegative) {
        this.name = name;
        this.type = type;
        this.currency = currency;
        this.allowNegative = allowNegative;
        this.balance = 0;
        this.createdAt = Instant.now();
    }

    /** Money leaves this account. */
    public void debit(long amount) {
        requirePositive(amount);
        if (!allowNegative && balance < amount) {
            throw new InsufficientFundsException(id, balance, amount);
        }
        balance -= amount;
    }

    /** Money arrives in this account. */
    public void credit(long amount) {
        requirePositive(amount);
        balance += amount;
    }

    private static void requirePositive(long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public AccountType getType() { return type; }
    public String getCurrency() { return currency; }
    public long getBalance() { return balance; }
    public boolean isAllowNegative() { return allowNegative; }
    public Instant getCreatedAt() { return createdAt; }
}
