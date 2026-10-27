package com.ledger.transaction;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TransferResponse(
        UUID transactionId,
        String description,
        Instant createdAt,
        List<EntryResponse> entries
) {
    public record EntryResponse(UUID accountId, EntryDirection direction, long amount) {
    }

    public static TransferResponse from(LedgerTransaction tx) {
        List<EntryResponse> entries = tx.getEntries().stream()
                .map(e -> new EntryResponse(e.getAccountId(), e.getDirection(), e.getAmount()))
                .toList();
        return new TransferResponse(tx.getId(), tx.getDescription(), tx.getCreatedAt(), entries);
    }
}
