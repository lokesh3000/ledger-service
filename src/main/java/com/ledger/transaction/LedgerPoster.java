package com.ledger.transaction;

import com.ledger.account.Account;
import com.ledger.common.BusinessRuleException;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * The ONE place where money moves. Transfers, hold captures and reversals all come through here,
 * so the double-entry rules cannot be skipped by accident.
 * Callers must already hold row locks on both accounts.
 */
@Component
public class LedgerPoster {

    private final LedgerTransactionRepository transactions;

    public LedgerPoster(LedgerTransactionRepository transactions) {
        this.transactions = transactions;
    }

    public LedgerTransaction post(Account from, Account to, long amount, String description, UUID reversalOf) {
        if (!from.getCurrency().equals(to.getCurrency())) {
            throw new BusinessRuleException("Currency mismatch between accounts: "
                    + from.getCurrency() + " vs " + to.getCurrency());
        }

        from.debit(amount);   // throws InsufficientFundsException when not allowed
        to.credit(amount);

        LedgerTransaction tx = new LedgerTransaction(description, reversalOf);
        tx.addEntry(from.getId(), EntryDirection.DEBIT, amount);
        tx.addEntry(to.getId(), EntryDirection.CREDIT, amount);
        tx.assertBalanced();

        return transactions.save(tx);
    }
}
