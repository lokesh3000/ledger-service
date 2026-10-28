package com.ledger.hold;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** Captures the full held amount into the destination account. */
public record CaptureHoldRequest(
        @NotNull UUID toAccountId,
        @Size(max = 255) String description
) {
    public String canonical(UUID holdId) {
        return String.join("|", holdId.toString(), toAccountId.toString(),
                description == null ? "" : description.trim());
    }
}
