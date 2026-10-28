# Ledger Service

A double-entry ledger built with Java 17, Spring Boot 3, PostgreSQL and Flyway.

## Features
- **Double-entry transfers**: every transfer is one DEBIT + one CREDIT, checked to balance, append-only
- **Idempotency**: `Idempotency-Key` on every write; the key and the money movement commit in one DB transaction
- **Concurrency safety**: pessimistic row locks taken in one global order (no deadlocks, no overdraft)
- **Holds**: authorize now, capture or release later (available = balance - held)
- **Reversals**: undo by posting a new linked transaction; at most one reversal per transaction (DB-enforced)
- **History**: paginated, newest-first entry history per account
- **Reconciliation**: recomputes balances and holds from entries on a consistent snapshot, on a timer and on demand
- **Proof**: Testcontainers integration tests run real concurrent load against PostgreSQL

## Run locally (PowerShell)
```powershell
psql -U postgres -c "CREATE DATABASE ledger;"
$env:DB_PASSWORD = "your-postgres-password"
.\mvnw.cmd test       # fast unit tests, no Docker needed
.\mvnw.cmd verify     # unit tests + concurrency integration tests (Docker Desktop must be running)
.\mvnw.cmd spring-boot:run
```

## API
| Method | Path | Notes |
|---|---|---|
| POST | /api/accounts | create an account |
| GET | /api/accounts/{id} | balance, held, available |
| GET | /api/accounts/{id}/entries?page=0&size=20 | paginated history (size max 100) |
| POST | /api/transfers | needs `Idempotency-Key` |
| GET | /api/transactions/{id} | entries of a transaction |
| POST | /api/transactions/{id}/reverse | needs `Idempotency-Key` |
| POST | /api/holds | needs `Idempotency-Key` |
| POST | /api/holds/{id}/capture | needs `Idempotency-Key` |
| POST | /api/holds/{id}/release | needs `Idempotency-Key` |
| GET | /api/holds/{id} | hold status |
| GET | /api/admin/reconciliation | consistency report |

Amounts are integers in minor units (cents). Import the Postman collections from `postman/` and run them in order.
