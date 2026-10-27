package com.ledger.account;

import java.time.Instant;
import java.util.UUID;

public record AccountResponse(
        UUID id,
        String name,
        AccountType type,
        String currency,
        long balance,
        boolean allowNegative,
        Instant createdAt
) {
    public static AccountResponse from(Account a) {
        return new AccountResponse(a.getId(), a.getName(), a.getType(), a.getCurrency(),
                a.getBalance(), a.isAllowNegative(), a.getCreatedAt());
    }
}
