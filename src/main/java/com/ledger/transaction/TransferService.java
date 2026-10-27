package com.ledger.transaction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledger.account.Account;
import com.ledger.account.AccountRepository;
import com.ledger.common.BadRequestException;
import com.ledger.common.BusinessRuleException;
import com.ledger.common.IdempotencyConflictException;
import com.ledger.common.NotFoundException;
import com.ledger.idempotency.Fingerprint;
import com.ledger.idempotency.IdempotencyStore;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

@Service
public class TransferService {

    private static final int MAX_KEY_LENGTH = 255;

    private final AccountRepository accounts;
    private final LedgerTransactionRepository transactions;
    private final IdempotencyStore idempotency;
    private final ObjectMapper objectMapper;

    public TransferService(AccountRepository accounts,
                           LedgerTransactionRepository transactions,
                           IdempotencyStore idempotency,
                           ObjectMapper objectMapper) {
        this.accounts = accounts;
        this.transactions = transactions;
        this.idempotency = idempotency;
        this.objectMapper = objectMapper;
    }

    /**
     * Everything below runs in ONE database transaction:
     * claim the idempotency key -> lock accounts -> move money -> write entries -> store the response.
     * If anything fails, all of it rolls back, including the key (so a failed request can be retried).
     */
    @Transactional
    public TransferResult transfer(String idempotencyKey, TransferRequest request) {
        validateKey(idempotencyKey);
        String hash = Fingerprint.sha256(request.canonical());

        if (!idempotency.tryAcquire(idempotencyKey, hash)) {
            return replay(idempotencyKey, hash);
        }

        TransferResponse response = execute(request);
        idempotency.complete(idempotencyKey, HttpStatus.CREATED.value(), toJson(response));
        return new TransferResult(response, false);
    }

    @Transactional(readOnly = true)
    public TransferResponse getTransaction(UUID id) {
        LedgerTransaction tx = transactions.findById(id)
                .orElseThrow(() -> new NotFoundException("Transaction not found: " + id));
        return TransferResponse.from(tx);
    }

    // ---------------------------------------------------------------- internals

    private TransferResult replay(String key, String hash) {
        IdempotencyStore.StoredResponse stored = idempotency.find(key)
                .orElseThrow(() -> new IllegalStateException("Idempotency row vanished for key " + key));
        if (!stored.requestHash().equals(hash)) {
            throw new IdempotencyConflictException(key);
        }
        if (stored.body() == null) {
            throw new IllegalStateException("Idempotency row has no stored response for key " + key);
        }
        return new TransferResult(fromJson(stored.body()), true);
    }

    private TransferResponse execute(TransferRequest request) {
        if (request.fromAccountId().equals(request.toAccountId())) {
            throw new BusinessRuleException("Cannot transfer to the same account");
        }

        // Lock both accounts in a fixed (sorted) order. Two opposite transfers
        // A->B and B->A then lock in the same order, so they cannot deadlock.
        List<UUID> lockOrder = Stream.of(request.fromAccountId(), request.toAccountId()).sorted().toList();
        Map<UUID, Account> locked = new HashMap<>();
        for (UUID id : lockOrder) {
            Account account = accounts.findByIdForUpdate(id)
                    .orElseThrow(() -> new NotFoundException("Account not found: " + id));
            locked.put(id, account);
        }

        Account from = locked.get(request.fromAccountId());
        Account to = locked.get(request.toAccountId());

        if (!from.getCurrency().equals(request.currency()) || !to.getCurrency().equals(request.currency())) {
            throw new BusinessRuleException("Currency mismatch: both accounts must use " + request.currency());
        }

        from.debit(request.amount());   // throws InsufficientFundsException if not allowed
        to.credit(request.amount());

        LedgerTransaction tx = new LedgerTransaction(request.description());
        tx.addEntry(from.getId(), EntryDirection.DEBIT, request.amount());
        tx.addEntry(to.getId(), EntryDirection.CREDIT, request.amount());
        tx.assertBalanced();

        transactions.save(tx);
        return TransferResponse.from(tx);
    }

    private static void validateKey(String key) {
        if (key == null || key.isBlank()) {
            throw new BadRequestException("Idempotency-Key header must not be blank");
        }
        if (key.length() > MAX_KEY_LENGTH) {
            throw new BadRequestException("Idempotency-Key must be at most " + MAX_KEY_LENGTH + " characters");
        }
    }

    private String toJson(TransferResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialise response", e);
        }
    }

    private TransferResponse fromJson(String json) {
        try {
            return objectMapper.readValue(json, TransferResponse.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not read stored response", e);
        }
    }
}
