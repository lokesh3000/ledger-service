package com.ledger.hold;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record PlaceHoldRequest(
        @NotNull UUID accountId,
        @Positive(message = "must be greater than 0") long amount,
        @NotBlank @Pattern(regexp = "[A-Z]{3}", message = "must be a 3-letter uppercase currency code, e.g. USD") String currency,
        @Size(max = 255) String description
) {
    public String canonical() {
        return String.join("|", accountId.toString(), Long.toString(amount), currency,
                description == null ? "" : description.trim());
    }
}
