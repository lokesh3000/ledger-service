package com.ledger.hold;

import java.time.Instant;
import java.util.UUID;

public record HoldResponse(
        UUID id,
        UUID accountId,
        long amount,
        String currency,
        HoldStatus status,
        String description,
        UUID captureTransactionId,
        Instant createdAt,
        Instant resolvedAt
) {
    public static HoldResponse from(Hold h) {
        return new HoldResponse(h.getId(), h.getAccountId(), h.getAmount(), h.getCurrency(), h.getStatus(),
                h.getDescription(), h.getCaptureTransactionId(), h.getCreatedAt(), h.getResolvedAt());
    }
}
