package com.ledger.common;

import java.util.UUID;

public class InsufficientFundsException extends BusinessRuleException {
    public InsufficientFundsException(UUID accountId, long available, long requested) {
        super("Insufficient funds in account " + accountId
                + ": available=" + available + ", requested=" + requested);
    }
}
