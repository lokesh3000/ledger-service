package com.ledger.reconciliation;

import java.time.Instant;
import java.util.List;

public record ReconciliationReport(
        Instant checkedAt,
        boolean consistent,
        long accountsChecked,
        long transactionsChecked,
        long entriesChecked,
        List<String> problems
) {
}
