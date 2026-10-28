package com.ledger;

import com.ledger.account.AccountResponse;
import com.ledger.account.AccountService;
import com.ledger.account.AccountType;
import com.ledger.account.CreateAccountRequest;
import com.ledger.common.InsufficientFundsException;
import com.ledger.common.BusinessRuleException;
import com.ledger.hold.CaptureHoldRequest;
import com.ledger.hold.HoldResponse;
import com.ledger.hold.HoldService;
import com.ledger.hold.HoldStatus;
import com.ledger.hold.PlaceHoldRequest;
import com.ledger.idempotency.IdempotencyExecutor;
import com.ledger.reconciliation.ReconciliationReport;
import com.ledger.reconciliation.ReconciliationService;
import com.ledger.transaction.ReversalService;
import com.ledger.transaction.TransferRequest;
import com.ledger.transaction.TransferResponse;
import com.ledger.transaction.TransferService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * These tests start a REAL PostgreSQL in Docker and hammer the service from many threads.
 * They prove the guarantees that unit tests cannot: no deadlocks, no double spend,
 * no duplicate execution of one idempotency key, and consistent books afterwards.
 * Run with: .\mvnw.cmd verify   (Docker Desktop must be running)
 */
@SpringBootTest
@Testcontainers
class ConcurrencyIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired AccountService accounts;
    @Autowired TransferService transfers;
    @Autowired ReversalService reversals;
    @Autowired HoldService holds;
    @Autowired ReconciliationService reconciliation;
    @Autowired JdbcTemplate jdbc;

    // ------------------------------------------------------------------ tests

    @Test
    void oppositeTransfersDoNotDeadlockAndConserveMoney() throws Exception {
        UUID bank = newAccount("Bank", AccountType.SYSTEM, true);
        UUID a = newAccount("A", AccountType.USER, false);
        UUID b = newAccount("B", AccountType.USER, false);
        fund(bank, a, 100_000);
        fund(bank, b, 100_000);

        List<Callable<Object>> tasks = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            UUID from = (i % 2 == 0) ? a : b;
            UUID to = (i % 2 == 0) ? b : a;
            tasks.add(() -> transfers.transfer("swap-" + UUID.randomUUID(),
                    new TransferRequest(from, to, 10, "USD", "swap")));
        }

        List<Result<Object>> results = runConcurrently(tasks);

        assertTrue(results.stream().allMatch(Result::succeeded), "no transfer may fail or deadlock");
        // 30 transfers each way, same amount: both balances end exactly where they started.
        assertEquals(100_000, accounts.get(a).balance());
        assertEquals(100_000, accounts.get(b).balance());
        assertConsistent();
    }

    @Test
    void sameIdempotencyKeyExecutesExactlyOnce() throws Exception {
        UUID bank = newAccount("Bank", AccountType.SYSTEM, true);
        UUID a = newAccount("A", AccountType.USER, false);

        String key = "same-key-" + UUID.randomUUID();
        String marker = "idem-" + key;
        TransferRequest request = new TransferRequest(bank, a, 500, "USD", marker);

        List<Callable<Object>> tasks = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            tasks.add(() -> transfers.transfer(key, request));
        }

        List<Result<Object>> results = runConcurrently(tasks);

        assertTrue(results.stream().allMatch(Result::succeeded));
        long replays = results.stream()
                .map(r -> (IdempotencyExecutor.Outcome<?>) r.value())
                .filter(IdempotencyExecutor.Outcome::replayed)
                .count();
        assertEquals(19, replays, "exactly one request executes, the other 19 are replays");

        Long created = jdbc.queryForObject(
                "SELECT count(*) FROM ledger_transactions WHERE description = ?", Long.class, marker);
        assertEquals(1L, created);
        assertEquals(500, accounts.get(a).balance());
        assertConsistent();
    }

    @Test
    void concurrentSpendingCannotOverdrawAnAccount() throws Exception {
        UUID bank = newAccount("Bank", AccountType.SYSTEM, true);
        UUID a = newAccount("A", AccountType.USER, false);
        UUID b = newAccount("B", AccountType.USER, false);
        fund(bank, a, 100);

        List<Callable<Object>> tasks = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            tasks.add(() -> transfers.transfer("spend-" + UUID.randomUUID(),
                    new TransferRequest(a, b, 10, "USD", "spend")));
        }

        List<Result<Object>> results = runConcurrently(tasks);

        long successes = results.stream().filter(Result::succeeded).count();
        assertEquals(10, successes, "100 / 10 = exactly 10 transfers can succeed");
        results.stream().filter(r -> !r.succeeded())
                .forEach(r -> assertInstanceOf(InsufficientFundsException.class, r.error()));
        assertEquals(0, accounts.get(a).balance());
        assertEquals(100, accounts.get(b).balance());
        assertConsistent();
    }

    @Test
    void holdCanOnlyBeCapturedOnceAndHeldMoneyIsNotSpendable() throws Exception {
        UUID bank = newAccount("Bank", AccountType.SYSTEM, true);
        UUID a = newAccount("A", AccountType.USER, false);
        UUID b = newAccount("B", AccountType.USER, false);
        fund(bank, a, 1_000);

        HoldResponse hold = holds.place("hold-" + UUID.randomUUID(),
                new PlaceHoldRequest(a, 600, "USD", "authorisation")).body();
        assertEquals(HoldStatus.ACTIVE, hold.status());
        assertEquals(400, accounts.get(a).available());

        // 500 > available (400), so this must fail even though balance is 1000
        assertThrows(InsufficientFundsException.class, () -> transfers.transfer(
                "too-much-" + UUID.randomUUID(), new TransferRequest(a, b, 500, "USD", "x")));

        List<Callable<Object>> tasks = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            tasks.add(() -> holds.capture("cap-" + UUID.randomUUID(), hold.id(),
                    new CaptureHoldRequest(b, "capture")));
        }
        List<Result<Object>> results = runConcurrently(tasks);

        assertEquals(1, results.stream().filter(Result::succeeded).count(), "only one capture may win");
        results.stream().filter(r -> !r.succeeded())
                .forEach(r -> assertInstanceOf(BusinessRuleException.class, r.error()));

        AccountResponse from = accounts.get(a);
        assertEquals(400, from.balance());
        assertEquals(0, from.held());
        assertEquals(600, accounts.get(b).balance());
        assertConsistent();
    }

    @Test
    void transactionCanOnlyBeReversedOnce() throws Exception {
        UUID bank = newAccount("Bank", AccountType.SYSTEM, true);
        UUID a = newAccount("A", AccountType.USER, false);
        UUID b = newAccount("B", AccountType.USER, false);
        fund(bank, a, 1_000);

        TransferResponse original = transfers.transfer("orig-" + UUID.randomUUID(),
                new TransferRequest(a, b, 300, "USD", "to be reversed")).body();

        List<Callable<Object>> tasks = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            tasks.add(() -> reversals.reverse("rev-" + UUID.randomUUID(), original.transactionId()));
        }
        List<Result<Object>> results = runConcurrently(tasks);

        assertEquals(1, results.stream().filter(Result::succeeded).count(), "only one reversal may win");
        assertEquals(1_000, accounts.get(a).balance());
        assertEquals(0, accounts.get(b).balance());
        assertConsistent();
    }

    // ------------------------------------------------------------------ helpers

    private UUID newAccount(String name, AccountType type, boolean allowNegative) {
        return accounts.create(new CreateAccountRequest(name, type, "USD", allowNegative)).id();
    }

    private void fund(UUID bank, UUID target, long amount) {
        transfers.transfer("seed-" + UUID.randomUUID(), new TransferRequest(bank, target, amount, "USD", "seed"));
    }

    private void assertConsistent() {
        ReconciliationReport report = reconciliation.run();
        assertTrue(report.consistent(), "books must reconcile, problems: " + report.problems());
    }

    private record Result<T>(T value, Throwable error) {
        static <T> Result<T> ok(T value) {
            return new Result<>(value, null);
        }

        static <T> Result<T> failed(Throwable error) {
            return new Result<>(null, error);
        }

        boolean succeeded() {
            return error == null;
        }
    }

    /** Starts all tasks at the same instant (latch) to maximise contention. */
    private <T> List<Result<T>> runConcurrently(List<Callable<T>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(tasks.size(), 16));
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Result<T>>> futures = new ArrayList<>();
            for (Callable<T> task : tasks) {
                futures.add(pool.submit(() -> {
                    start.await();
                    try {
                        return Result.ok(task.call());
                    } catch (Throwable t) {
                        return Result.failed(t);
                    }
                }));
            }
            start.countDown();

            List<Result<T>> results = new ArrayList<>();
            for (Future<Result<T>> f : futures) {
                results.add(f.get(60, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }
}
