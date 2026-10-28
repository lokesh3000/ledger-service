package com.ledger.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ledger.common.BadRequestException;
import com.ledger.common.IdempotencyConflictException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.Supplier;

/**
 * Runs any write operation exactly once per Idempotency-Key.
 *
 * Everything happens in ONE database transaction:
 *   claim key -> run the operation -> store the response -> commit.
 * If the operation throws, the whole transaction (including the key row) rolls back,
 * so a failed request can be retried with the same key.
 */
@Service
public class IdempotencyExecutor {

    public record Outcome<T>(T body, boolean replayed) {
    }

    private static final int MAX_KEY_LENGTH = 255;

    private final IdempotencyStore store;
    private final ObjectMapper objectMapper;

    public IdempotencyExecutor(IdempotencyStore store, ObjectMapper objectMapper) {
        this.store = store;
        this.objectMapper = objectMapper;
    }

    /**
     * @param operation        name of the operation, mixed into the fingerprint so the same key
     *                         cannot be reused across different endpoints
     * @param canonicalRequest stable string describing the request payload
     */
    @Transactional
    public <T> Outcome<T> execute(String key, String operation, String canonicalRequest,
                                  int successStatus, Class<T> responseType, Supplier<T> action) {
        validateKey(key);
        String hash = Fingerprint.sha256(operation + "|" + canonicalRequest);

        if (!store.tryAcquire(key, hash)) {
            return replay(key, hash, responseType);
        }

        T result = action.get();
        store.complete(key, successStatus, toJson(result));
        return new Outcome<>(result, false);
    }

    private <T> Outcome<T> replay(String key, String hash, Class<T> responseType) {
        IdempotencyStore.StoredResponse stored = store.find(key)
                .orElseThrow(() -> new IllegalStateException("Idempotency row vanished for key " + key));
        if (!stored.requestHash().equals(hash)) {
            throw new IdempotencyConflictException(key);
        }
        if (stored.body() == null) {
            throw new IllegalStateException("Idempotency row has no stored response for key " + key);
        }
        return new Outcome<>(fromJson(stored.body(), responseType), true);
    }

    private static void validateKey(String key) {
        if (key == null || key.isBlank()) {
            throw new BadRequestException("Idempotency-Key header must not be blank");
        }
        if (key.length() > MAX_KEY_LENGTH) {
            throw new BadRequestException("Idempotency-Key must be at most " + MAX_KEY_LENGTH + " characters");
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialise response", e);
        }
    }

    private <T> T fromJson(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not read stored response", e);
        }
    }
}
