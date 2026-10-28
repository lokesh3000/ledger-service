package com.ledger.history;

import com.ledger.account.AccountRepository;
import com.ledger.common.BadRequestException;
import com.ledger.common.NotFoundException;
import com.ledger.common.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class HistoryService {

    private static final int MAX_PAGE_SIZE = 100;

    private final AccountRepository accounts;
    private final LedgerEntryRepository entries;

    public HistoryService(AccountRepository accounts, LedgerEntryRepository entries) {
        this.accounts = accounts;
        this.entries = entries;
    }

    @Transactional(readOnly = true)
    public PageResponse<EntryHistoryItem> history(UUID accountId, int page, int size) {
        if (page < 0) {
            throw new BadRequestException("page must be 0 or greater");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new BadRequestException("size must be between 1 and " + MAX_PAGE_SIZE);
        }
        if (!accounts.existsById(accountId)) {
            throw new NotFoundException("Account not found: " + accountId);
        }
        return PageResponse.from(entries.findHistory(accountId, PageRequest.of(page, size)));
    }
}
