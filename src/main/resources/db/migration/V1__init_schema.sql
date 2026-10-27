-- Money is stored as BIGINT minor units (e.g. cents). Never floating point.
-- Convention in this ledger: balance = total CREDITS - total DEBITS.
-- Money leaving an account is a DEBIT; money arriving is a CREDIT.

CREATE TABLE accounts (
    id             UUID PRIMARY KEY,
    name           VARCHAR(100) NOT NULL,
    type           VARCHAR(20)  NOT NULL,
    currency       VARCHAR(3)   NOT NULL,
    balance        BIGINT       NOT NULL DEFAULT 0,
    allow_negative BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_account_type CHECK (type IN ('USER', 'MERCHANT', 'SYSTEM')),
    -- Last line of defence: even a buggy app cannot overdraw a normal account.
    CONSTRAINT chk_balance_non_negative CHECK (allow_negative OR balance >= 0)
);

CREATE TABLE ledger_transactions (
    id          UUID PRIMARY KEY,
    description VARCHAR(255),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Entries are append-only: the application never updates or deletes them.
CREATE TABLE ledger_entries (
    id             UUID PRIMARY KEY,
    transaction_id UUID        NOT NULL REFERENCES ledger_transactions (id),
    account_id     UUID        NOT NULL REFERENCES accounts (id),
    direction      VARCHAR(6)  NOT NULL,
    amount         BIGINT      NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_entry_direction CHECK (direction IN ('DEBIT', 'CREDIT')),
    CONSTRAINT chk_entry_amount_positive CHECK (amount > 0)
);

CREATE INDEX idx_entries_account ON ledger_entries (account_id, created_at);
CREATE INDEX idx_entries_transaction ON ledger_entries (transaction_id);

-- One row per Idempotency-Key. Written in the SAME DB transaction as the transfer.
CREATE TABLE idempotency_keys (
    idem_key      VARCHAR(255) PRIMARY KEY,
    request_hash  VARCHAR(64)  NOT NULL,
    status_code   INT,
    response_body TEXT,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
