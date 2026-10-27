package com.ledger.common;

/** A well-formed request that breaks a business rule (maps to HTTP 422). */
public class BusinessRuleException extends RuntimeException {
    public BusinessRuleException(String message) {
        super(message);
    }
}
