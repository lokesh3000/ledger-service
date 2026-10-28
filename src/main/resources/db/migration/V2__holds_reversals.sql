-- Holds: money reserved on an account but not yet moved (authorize now, capture later).
ALTER TABLE accounts ADD COLUMN held BIGINT NOT NULL DEFAULT 0;
ALTER TABLE accounts ADD CONSTRAINT chk_held_non_negative CHECK (held >= 0);
ALTER TABLE accounts ADD CONSTRAINT chk_held_within_balance CHECK (allow_negative OR held <= balance);

-- Reversals: a new transaction that points back at the one it undoes.
ALTER TABLE ledger_transactions ADD COLUMN reversal_of UUID REFERENCES ledger_transactions (id);
-- At most ONE reversal per transaction, enforced by the database itself.
CREATE UNIQUE INDEX uq_transactions_reversal_of
    ON ledger_transactions (reversal_of) WHERE reversal_of IS NOT NULL;

CREATE TABLE holds (
    id                     UUID PRIMARY KEY,
    account_id             UUID         NOT NULL REFERENCES accounts (id),
    amount                 BIGINT       NOT NULL,
    currency               VARCHAR(3)   NOT NULL,
    status                 VARCHAR(10)  NOT NULL,
    description            VARCHAR(255),
    capture_transaction_id UUID REFERENCES ledger_transactions (id),
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    resolved_at            TIMESTAMPTZ,
    CONSTRAINT chk_hold_amount_positive CHECK (amount > 0),
    CONSTRAINT chk_hold_status CHECK (status IN ('ACTIVE', 'CAPTURED', 'RELEASED'))
);

CREATE INDEX idx_holds_account_status ON holds (account_id, status);
