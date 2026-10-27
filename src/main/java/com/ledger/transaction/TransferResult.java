package com.ledger.transaction;

/** What the service hands back to the controller: the body, and whether it was a replay. */
public record TransferResult(TransferResponse response, boolean replayed) {
}
