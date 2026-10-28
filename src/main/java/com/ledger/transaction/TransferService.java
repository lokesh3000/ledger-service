package com.ledger.transaction;

import com.ledger.account.Account;
import com.ledger.account.AccountLocker;
import com.ledger.common.BusinessRuleException;
import com.ledger.common.NotFoundException;
import com.ledger.idempotency.IdempotencyExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class TransferService {

    private final IdempotencyExecutor idempotency;
    private final AccountLocker locker;
    private final LedgerPoster poster;
    private final LedgerTransactionRepository transactions;

    public TransferService(IdempotencyExecutor idempotency,
                           AccountLocker locker,
                           LedgerPoster poster,
                           LedgerTransactionRepository transactions) {
        this.idempotency = idempotency;
        this.locker = locker;
        this.poster = poster;
        this.transactions = transactions;
    }

    public IdempotencyExecutor.Outcome<TransferResponse> transfer(String idempotencyKey, TransferRequest request) {
        return idempotency.execute(idempotencyKey, "TRANSFER", request.canonical(), 201,
                TransferResponse.class, () -> doTransfer(request));
    }

    @Transactional(readOnly = true)
    public TransferResponse getTransaction(UUID id) {
        LedgerTransaction tx = transactions.findById(id)
                .orElseThrow(() -> new NotFoundException("Transaction not found: " + id));
        return TransferResponse.from(tx);
    }

    private TransferResponse doTransfer(TransferRequest request) {
        if (request.fromAccountId().equals(request.toAccountId())) {
            throw new BusinessRuleException("Cannot transfer to the same account");
        }

        Map<UUID, Account> locked = locker.lockInOrder(request.fromAccountId(), request.toAccountId());
        Account from = locked.get(request.fromAccountId());
        Account to = locked.get(request.toAccountId());

        if (!from.getCurrency().equals(request.currency()) || !to.getCurrency().equals(request.currency())) {
            throw new BusinessRuleException("Currency mismatch: both accounts must use " + request.currency());
        }

        LedgerTransaction tx = poster.post(from, to, request.amount(), request.description(), null);
        return TransferResponse.from(tx);
    }
}
