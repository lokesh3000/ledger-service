package com.ledger.history;

import com.ledger.transaction.EntryDirection;

import java.time.Instant;
import java.util.UUID;

public record EntryHistoryItem(
        UUID entryId,
        UUID transactionId,
        EntryDirection direction,
        long amount,
        Instant createdAt
) {
}
