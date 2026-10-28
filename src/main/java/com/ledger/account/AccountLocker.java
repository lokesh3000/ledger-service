package com.ledger.account;

import com.ledger.common.NotFoundException;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Locks accounts (SELECT ... FOR UPDATE) in ONE global order: sorted by id.
 * Every code path that needs several accounts goes through here, so two transactions
 * can never wait on each other in a circle (no deadlocks).
 */
@Component
public class AccountLocker {

    private final AccountRepository accounts;

    public AccountLocker(AccountRepository accounts) {
        this.accounts = accounts;
    }

    public Map<UUID, Account> lockInOrder(UUID... ids) {
        List<UUID> ordered = Stream.of(ids).distinct().sorted().toList();
        Map<UUID, Account> locked = new HashMap<>();
        for (UUID id : ordered) {
            Account account = accounts.findByIdForUpdate(id)
                    .orElseThrow(() -> new NotFoundException("Account not found: " + id));
            locked.put(id, account);
        }
        return locked;
    }
}
