package com.ledger.account;

import com.ledger.common.InsufficientFundsException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AccountTest {

    @Test
    void creditThenDebitUpdatesBalance() {
        Account a = new Account("Alice", AccountType.USER, "USD", false);
        a.credit(10_000);
        a.debit(2_500);
        assertEquals(7_500, a.getBalance());
    }

    @Test
    void normalAccountCannotGoNegative() {
        Account a = new Account("Alice", AccountType.USER, "USD", false);
        a.credit(100);
        assertThrows(InsufficientFundsException.class, () -> a.debit(101));
        assertEquals(100, a.getBalance());
    }

    @Test
    void systemAccountMayGoNegative() {
        Account sys = new Account("Bank", AccountType.SYSTEM, "USD", true);
        sys.debit(5_000);
        assertEquals(-5_000, sys.getBalance());
    }

    @Test
    void nonPositiveAmountsAreRejected() {
        Account a = new Account("Alice", AccountType.USER, "USD", false);
        assertThrows(IllegalArgumentException.class, () -> a.credit(0));
        assertThrows(IllegalArgumentException.class, () -> a.debit(-5));
    }

    @Test
    void holdReducesAvailableButNotBalance() {
        Account a = new Account("Alice", AccountType.USER, "USD", false);
        a.credit(1_000);
        a.placeHold(600);
        assertEquals(1_000, a.getBalance());
        assertEquals(600, a.getHeld());
        assertEquals(400, a.available());
    }

    @Test
    void heldMoneyCannotBeSpent() {
        Account a = new Account("Alice", AccountType.USER, "USD", false);
        a.credit(1_000);
        a.placeHold(600);
        assertThrows(InsufficientFundsException.class, () -> a.debit(500));
        a.debit(400);   // exactly the available amount is fine
        assertEquals(600, a.getBalance());
    }

    @Test
    void cannotHoldMoreThanAvailable() {
        Account a = new Account("Alice", AccountType.USER, "USD", false);
        a.credit(1_000);
        a.placeHold(700);
        assertThrows(InsufficientFundsException.class, () -> a.placeHold(400));
    }

    @Test
    void releaseRestoresAvailable() {
        Account a = new Account("Alice", AccountType.USER, "USD", false);
        a.credit(1_000);
        a.placeHold(600);
        a.releaseHold(600);
        assertEquals(0, a.getHeld());
        assertEquals(1_000, a.available());
    }

    @Test
    void cannotReleaseMoreThanHeld() {
        Account a = new Account("Alice", AccountType.USER, "USD", false);
        a.credit(1_000);
        a.placeHold(100);
        assertThrows(IllegalStateException.class, () -> a.releaseHold(200));
    }
}
