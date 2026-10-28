package com.ledger.hold;

import com.ledger.common.BusinessRuleException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HoldTest {

    private Hold newHold() {
        return new Hold(UUID.randomUUID(), 500, "USD", "test");
    }

    @Test
    void startsActive() {
        assertEquals(HoldStatus.ACTIVE, newHold().getStatus());
    }

    @Test
    void captureResolvesTheHold() {
        Hold h = newHold();
        UUID txId = UUID.randomUUID();
        h.markCaptured(txId);
        assertEquals(HoldStatus.CAPTURED, h.getStatus());
        assertEquals(txId, h.getCaptureTransactionId());
        assertNotNull(h.getResolvedAt());
    }

    @Test
    void cannotCaptureTwice() {
        Hold h = newHold();
        h.markCaptured(UUID.randomUUID());
        assertThrows(BusinessRuleException.class, () -> h.markCaptured(UUID.randomUUID()));
    }

    @Test
    void cannotReleaseAfterCapture() {
        Hold h = newHold();
        h.markCaptured(UUID.randomUUID());
        assertThrows(BusinessRuleException.class, h::markReleased);
    }

    @Test
    void cannotCaptureAfterRelease() {
        Hold h = newHold();
        h.markReleased();
        assertThrows(BusinessRuleException.class, () -> h.markCaptured(UUID.randomUUID()));
    }
}
