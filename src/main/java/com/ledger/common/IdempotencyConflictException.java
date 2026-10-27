package com.ledger.common;

/** Same Idempotency-Key reused with a different request body. */
public class IdempotencyConflictException extends BusinessRuleException {
    public IdempotencyConflictException(String key) {
        super("Idempotency-Key '" + key + "' was already used with a different request");
    }
}
