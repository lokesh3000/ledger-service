package com.ledger.transaction;

import com.ledger.account.Account;
import com.ledger.account.AccountLocker;
import com.ledger.common.BusinessRuleException;
import com.ledger.common.NotFoundException;
import com.ledger.idempotency.IdempotencyExecutor;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

/**
 * Ledgers never edit or delete history. To undo a transaction we post a NEW transaction
 * with the opposite entries, linked to the original through reversal_of.
 */
@Service
public class ReversalService {

    private final IdempotencyExecutor idempotency;
    private final AccountLocker locker;
    private final LedgerPoster poster;
    private final LedgerTransactionRepository transactions;

    public ReversalService(IdempotencyExecutor idempotency,
                           AccountLocker locker,
                           LedgerPoster poster,
                           LedgerTransactionRepository transactions) {
        this.idempotency = idempotency;
        this.locker = locker;
        this.poster = poster;
        this.transactions = transactions;
    }

    public IdempotencyExecutor.Outcome<TransferResponse> reverse(String idempotencyKey, UUID transactionId) {
        return idempotency.execute(idempotencyKey, "REVERSE", transactionId.toString(), 201,
                TransferResponse.class, () -> doReverse(transactionId));
    }

    private TransferResponse doReverse(UUID transactionId) {
        LedgerTransaction original = transactions.findById(transactionId)
                .orElseThrow(() -> new NotFoundException("Transaction not found: " + transactionId));

        if (original.getReversalOf() != null) {
            throw new BusinessRuleException("A reversal cannot itself be reversed");
        }

        LedgerEntry debit = entryOf(original, EntryDirection.DEBIT);
        LedgerEntry credit = entryOf(original, EntryDirection.CREDIT);

        // Lock first, THEN check "already reversed": two concurrent reversals are serialised by
        // the account locks, so the second one reliably sees the first one's committed reversal.
        Map<UUID, Account> locked = locker.lockInOrder(debit.getAccountId(), credit.getAccountId());

        if (transactions.existsByReversalOf(transactionId)) {
            throw new BusinessRuleException("Transaction " + transactionId + " has already been reversed");
        }

        Account originalSender = locked.get(debit.getAccountId());
        Account originalReceiver = locked.get(credit.getAccountId());

        // The receiver pays the money back. If they already spent it, this fails with 422.
        LedgerTransaction reversal = poster.post(
                originalReceiver, originalSender, credit.getAmount(),
                "Reversal of " + transactionId, transactionId);
        return TransferResponse.from(reversal);
    }

    private static LedgerEntry entryOf(LedgerTransaction tx, EntryDirection direction) {
        return tx.getEntries().stream()
                .filter(e -> e.getDirection() == direction)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Transaction " + tx.getId() + " has no " + direction + " entry"));
    }
}
