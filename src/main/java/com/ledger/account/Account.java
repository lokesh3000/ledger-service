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
 * An account holds a cached balance in minor units (cents) plus an amount "held" by active holds.
 * available = balance - held. Ledger entries are the source of truth; the reconciliation job
 * verifies balance and held against them.
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

    @Column(nullable = false)
    private long held;

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
        this.held = 0;
        this.createdAt = Instant.now();
    }

    public long available() {
        return balance - held;
    }

    /** Money leaves this account. Held money is not spendable. */
    public void debit(long amount) {
        requirePositive(amount);
        if (!allowNegative && available() < amount) {
            throw new InsufficientFundsException(id, available(), amount);
        }
        balance -= amount;
    }

    /** Money arrives in this account. */
    public void credit(long amount) {
        requirePositive(amount);
        balance += amount;
    }

    /** Reserve money without moving it. */
    public void placeHold(long amount) {
        requirePositive(amount);
        if (!allowNegative && available() < amount) {
            throw new InsufficientFundsException(id, available(), amount);
        }
        held += amount;
    }

    /** Un-reserve money (hold released, or about to be captured). */
    public void releaseHold(long amount) {
        requirePositive(amount);
        if (amount > held) {
            throw new IllegalStateException("Cannot release " + amount + " when only " + held + " is held");
        }
        held -= amount;
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
    public long getHeld() { return held; }
    public boolean isAllowNegative() { return allowNegative; }
    public Instant getCreatedAt() { return createdAt; }
}
