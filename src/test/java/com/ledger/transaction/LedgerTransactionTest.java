package com.ledger.transaction;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LedgerTransactionTest {

    @Test
    void balancedTransactionPasses() {
        LedgerTransaction tx = new LedgerTransaction("ok");
        tx.addEntry(UUID.randomUUID(), EntryDirection.DEBIT, 100);
        tx.addEntry(UUID.randomUUID(), EntryDirection.CREDIT, 100);
        assertDoesNotThrow(tx::assertBalanced);
    }

    @Test
    void unbalancedTransactionIsRejected() {
        LedgerTransaction tx = new LedgerTransaction("bad");
        tx.addEntry(UUID.randomUUID(), EntryDirection.DEBIT, 100);
        tx.addEntry(UUID.randomUUID(), EntryDirection.CREDIT, 90);
        assertThrows(IllegalStateException.class, tx::assertBalanced);
    }

    @Test
    void singleEntryTransactionIsRejected() {
        LedgerTransaction tx = new LedgerTransaction("lonely");
        tx.addEntry(UUID.randomUUID(), EntryDirection.DEBIT, 100);
        assertThrows(IllegalStateException.class, tx::assertBalanced);
    }
}
