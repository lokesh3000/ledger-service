# Ledger Service

A double-entry ledger built with Java 17, Spring Boot 3, PostgreSQL and Flyway.

## Phase 1 (v0.1) - what exists
- Accounts (USER, MERCHANT, SYSTEM) with balances in minor units (cents)
- Transfers recorded as balanced DEBIT + CREDIT entries (append-only)
- Idempotency-Key on every transfer (safe retries, no double spend)
- Pessimistic row locking with a fixed lock order (no deadlocks on opposite transfers)
- Flyway-managed schema with DB-level safety constraints

## Run locally (PowerShell)
```powershell
psql -U postgres -c "CREATE DATABASE ledger;"
$env:DB_PASSWORD = "your-postgres-password"
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run
```

## API
| Method | Path | Notes |
|---|---|---|
| POST | /api/accounts | create an account |
| GET | /api/accounts/{id} | get an account and its balance |
| POST | /api/transfers | header `Idempotency-Key` required |
| GET | /api/transactions/{id} | view the entries of a transaction |

Import `postman/ledger-phase1.postman_collection.json` into Postman and run the requests in order.
