package com.ledger.account;

import com.ledger.common.BusinessRuleException;
import com.ledger.common.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AccountService {

    private final AccountRepository accounts;

    public AccountService(AccountRepository accounts) {
        this.accounts = accounts;
    }

    @Transactional
    public AccountResponse create(CreateAccountRequest request) {
        boolean allowNegative = Boolean.TRUE.equals(request.allowNegative());
        if (allowNegative && request.type() != AccountType.SYSTEM) {
            throw new BusinessRuleException("Only SYSTEM accounts may allow a negative balance");
        }
        Account saved = accounts.save(
                new Account(request.name().trim(), request.type(), request.currency(), allowNegative));
        return AccountResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public AccountResponse get(UUID id) {
        return accounts.findById(id)
                .map(AccountResponse::from)
                .orElseThrow(() -> new NotFoundException("Account not found: " + id));
    }
}
