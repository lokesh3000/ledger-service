package com.ledger.idempotency;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

public final class IdempotentResponse {

    private IdempotentResponse() {
    }

    /** First execution -> the normal status. Replay -> 200 plus an Idempotent-Replayed header. */
    public static <T> ResponseEntity<T> of(IdempotencyExecutor.Outcome<T> outcome, HttpStatus firstTimeStatus) {
        if (outcome.replayed()) {
            return ResponseEntity.ok()
                    .header("Idempotent-Replayed", "true")
                    .body(outcome.body());
        }
        return ResponseEntity.status(firstTimeStatus).body(outcome.body());
    }
}
