package com.ledger.transaction;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** A group of entries that must balance: total debits == total credits. */
@Entity
@Table(name = "ledger_transactions")
public class LedgerTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(length = 255)
    private String description;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    // "DEBIT" sorts after "CREDIT" alphabetically, so DESC lists the debit first.
    @OneToMany(mappedBy = "transaction", cascade = CascadeType.ALL)
    @OrderBy("direction DESC")
    private List<LedgerEntry> entries = new ArrayList<>();

    protected LedgerTransaction() {
        // required by JPA
    }

    public LedgerTransaction(String description) {
        this.description = description;
        this.createdAt = Instant.now();
    }

    public void addEntry(UUID accountId, EntryDirection direction, long amount) {
        entries.add(new LedgerEntry(this, accountId, direction, amount));
    }

    /** The core double-entry invariant. Called before every save. */
    public void assertBalanced() {
        long debits = sum(EntryDirection.DEBIT);
        long credits = sum(EntryDirection.CREDIT);
        if (entries.size() < 2 || debits != credits) {
            throw new IllegalStateException(
                    "Unbalanced transaction: debits=" + debits + ", credits=" + credits);
        }
    }

    private long sum(EntryDirection direction) {
        return entries.stream()
                .filter(e -> e.getDirection() == direction)
                .mapToLong(LedgerEntry::getAmount)
                .sum();
    }

    public UUID getId() { return id; }
    public String getDescription() { return description; }
    public Instant getCreatedAt() { return createdAt; }
    public List<LedgerEntry> getEntries() { return entries; }
}
