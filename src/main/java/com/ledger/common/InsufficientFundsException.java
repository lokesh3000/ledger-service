package com.ledger.common;

import java.util.UUID;

public class InsufficientFundsException extends BusinessRuleException {
    public InsufficientFundsException(UUID accountId, long balance, long requested) {
        super("Insufficient funds in account " + accountId
                + ": balance=" + balance + ", requested=" + requested);
    }
}
