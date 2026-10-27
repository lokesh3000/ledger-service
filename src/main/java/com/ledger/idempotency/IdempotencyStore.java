package com.ledger.idempotency;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Stores one row per Idempotency-Key.
 *
 * It deliberately uses the same DB transaction as the transfer (no REQUIRES_NEW):
 * the key row and the money movement commit together or not at all.
 */
@Component
public class IdempotencyStore {

    public record StoredResponse(String requestHash, Integer statusCode, String body) {
    }

    private final JdbcTemplate jdbc;

    public IdempotencyStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Atomically claims the key. Returns true if this transaction inserted it.
     * If another transaction holds an uncommitted insert of the same key, PostgreSQL
     * makes this statement WAIT until that transaction commits or rolls back.
     */
    public boolean tryAcquire(String key, String requestHash) {
        int rows = jdbc.update(
                "INSERT INTO idempotency_keys (idem_key, request_hash) VALUES (?, ?) "
                        + "ON CONFLICT (idem_key) DO NOTHING",
                key, requestHash);
        return rows == 1;
    }

    public Optional<StoredResponse> find(String key) {
        return jdbc.query(
                        "SELECT request_hash, status_code, response_body FROM idempotency_keys WHERE idem_key = ?",
                        (rs, i) -> new StoredResponse(
                                rs.getString("request_hash"),
                                rs.getObject("status_code", Integer.class),
                                rs.getString("response_body")),
                        key)
                .stream()
                .findFirst();
    }

    public void complete(String key, int statusCode, String responseBody) {
        jdbc.update(
                "UPDATE idempotency_keys SET status_code = ?, response_body = ? WHERE idem_key = ?",
                statusCode, responseBody, key);
    }
}
