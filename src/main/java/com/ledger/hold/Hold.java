package com.ledger.hold;

import com.ledger.common.BusinessRuleException;
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

/** Reserved money. ACTIVE -> CAPTURED (money moved) or ACTIVE -> RELEASED (reservation dropped). */
@Entity
@Table(name = "holds")
public class Hold {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(nullable = false)
    private long amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private HoldStatus status;

    @Column(length = 255)
    private String description;

    @Column(name = "capture_transaction_id")
    private UUID captureTransactionId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    protected Hold() {
        // required by JPA
    }

    public Hold(UUID accountId, long amount, String currency, String description) {
        this.accountId = accountId;
        this.amount = amount;
        this.currency = currency;
        this.description = description;
        this.status = HoldStatus.ACTIVE;
        this.createdAt = Instant.now();
    }

    public void requireActive() {
        if (status != HoldStatus.ACTIVE) {
            throw new BusinessRuleException("Hold " + id + " is already " + status);
        }
    }

    public void markCaptured(UUID transactionId) {
        requireActive();
        this.status = HoldStatus.CAPTURED;
        this.captureTransactionId = transactionId;
        this.resolvedAt = Instant.now();
    }

    public void markReleased() {
        requireActive();
        this.status = HoldStatus.RELEASED;
        this.resolvedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getAccountId() { return accountId; }
    public long getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public HoldStatus getStatus() { return status; }
    public String getDescription() { return description; }
    public UUID getCaptureTransactionId() { return captureTransactionId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getResolvedAt() { return resolvedAt; }
}
