package com.ledger.reconciliation;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Proves the books are consistent by recomputing everything from the immutable entries:
 *   1. every transaction has debits == credits (and at least one entry)
 *   2. every account balance == sum(credits) - sum(debits)
 *   3. every account's held amount == sum of its ACTIVE holds
 *
 * It runs at REPEATABLE READ so all queries see ONE snapshot of the database.
 * At the default READ COMMITTED, a transfer committing between two of the queries
 * could produce false alarms.
 */
@Service
public class ReconciliationService {

    private static final String SIGNED_SUM =
            "SUM(CASE WHEN e.direction = 'CREDIT' THEN e.amount ELSE -e.amount END)";

    private final JdbcTemplate jdbc;

    public ReconciliationService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ReconciliationReport run() {
        List<String> problems = new ArrayList<>();

        // 1a. unbalanced transactions
        jdbc.query("""
                SELECT e.transaction_id,
                       SUM(CASE WHEN e.direction = 'DEBIT'  THEN e.amount ELSE 0 END) AS debits,
                       SUM(CASE WHEN e.direction = 'CREDIT' THEN e.amount ELSE 0 END) AS credits
                FROM ledger_entries e
                GROUP BY e.transaction_id
                HAVING SUM(CASE WHEN e.direction = 'DEBIT'  THEN e.amount ELSE 0 END)
                    <> SUM(CASE WHEN e.direction = 'CREDIT' THEN e.amount ELSE 0 END)
                """, (RowCallbackHandler) rs -> problems.add(
                "Unbalanced transaction " + rs.getObject("transaction_id")
                        + ": debits=" + rs.getLong("debits") + ", credits=" + rs.getLong("credits")));

        // 1b. transactions with no entries at all
        jdbc.query("""
                SELECT t.id
                FROM ledger_transactions t
                LEFT JOIN ledger_entries e ON e.transaction_id = t.id
                WHERE e.id IS NULL
                """, (RowCallbackHandler) rs -> problems.add(
                "Transaction without entries " + rs.getObject("id")));

        // 2. cached balance vs recomputed balance
        jdbc.query("SELECT a.id, a.balance, COALESCE(" + SIGNED_SUM + ", 0) AS computed "
                + "FROM accounts a LEFT JOIN ledger_entries e ON e.account_id = a.id "
                + "GROUP BY a.id, a.balance "
                + "HAVING a.balance <> COALESCE(" + SIGNED_SUM + ", 0)",
                (RowCallbackHandler) rs -> problems.add(
                        "Balance mismatch on account " + rs.getObject("id")
                                + ": stored=" + rs.getLong("balance") + ", computed=" + rs.getLong("computed")));

        // 3. held amount vs active holds
        jdbc.query("""
                SELECT a.id, a.held,
                       COALESCE((SELECT SUM(h.amount) FROM holds h
                                 WHERE h.account_id = a.id AND h.status = 'ACTIVE'), 0) AS computed
                FROM accounts a
                WHERE a.held <> COALESCE((SELECT SUM(h.amount) FROM holds h
                                          WHERE h.account_id = a.id AND h.status = 'ACTIVE'), 0)
                """, (RowCallbackHandler) rs -> problems.add(
                "Held mismatch on account " + rs.getObject("id")
                        + ": stored=" + rs.getLong("held") + ", computed=" + rs.getLong("computed")));

        return new ReconciliationReport(
                Instant.now(),
                problems.isEmpty(),
                count("accounts"),
                count("ledger_transactions"),
                count("ledger_entries"),
                problems);
    }

    private long count(String table) {
        Long n = jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class);
        return n == null ? 0 : n;
    }
}
