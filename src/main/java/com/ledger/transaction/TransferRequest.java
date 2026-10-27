package com.ledger.transaction;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** amount is in minor units (e.g. 2500 = 25.00). */
public record TransferRequest(
        @NotNull UUID fromAccountId,
        @NotNull UUID toAccountId,
        @Positive(message = "must be greater than 0") long amount,
        @NotBlank @Pattern(regexp = "[A-Z]{3}", message = "must be a 3-letter uppercase currency code, e.g. USD") String currency,
        @Size(max = 255) String description
) {
    /** Stable string used to fingerprint the request for idempotency checks. */
    public String canonical() {
        return String.join("|",
                fromAccountId.toString(),
                toAccountId.toString(),
                Long.toString(amount),
                currency,
                description == null ? "" : description.trim());
    }
}
