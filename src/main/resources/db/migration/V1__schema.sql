-- LootLedger core schema: double-entry accounts, postings, idempotency, outbox, saga, audit.

-- An account holds a balance of one asset (gold, or a specific item type) for one owner.
CREATE TABLE account (
    id       BIGSERIAL PRIMARY KEY,
    owner_id BIGINT NOT NULL,                 -- player id, or a system account owner id
    asset    TEXT   NOT NULL,                 -- 'GOLD', 'ITEM:legendary_sword', ...
    kind     TEXT   NOT NULL,                 -- 'PLAYER', 'FAUCET' (mint), 'SINK' (burn), 'ESCROW'
    balance  BIGINT NOT NULL DEFAULT 0,       -- derived cache; source of truth is the ledger
    version  BIGINT NOT NULL DEFAULT 0,       -- optimistic lock
    CONSTRAINT uq_account_owner_asset UNIQUE (owner_id, asset)
);

CREATE INDEX idx_account_kind_asset ON account (kind, asset);

-- A transfer is one logical operation; it has >= 2 balanced postings.
CREATE TABLE transfer (
    id          BIGSERIAL PRIMARY KEY,
    external_id UUID        NOT NULL,          -- from the idempotency key / event key
    type        TEXT        NOT NULL,          -- 'TRADE', 'LOOT', 'AUCTION_SETTLE', ...
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_transfer_external_id UNIQUE (external_id)
);

-- Postings: debits and credits. Sum of amounts per transfer per asset MUST be zero.
CREATE TABLE posting (
    id          BIGSERIAL PRIMARY KEY,
    transfer_id BIGINT NOT NULL REFERENCES transfer (id),
    account_id  BIGINT NOT NULL REFERENCES account (id),
    asset       TEXT   NOT NULL,
    amount      BIGINT NOT NULL               -- negative = debit, positive = credit
);

CREATE INDEX idx_posting_transfer ON posting (transfer_id);
CREATE INDEX idx_posting_account ON posting (account_id);

-- Idempotency records (the load-bearing table).
CREATE TABLE idempotency_key (
    key           TEXT PRIMARY KEY,           -- client-supplied Idempotency-Key
    request_hash  TEXT        NOT NULL,       -- hash of the request body
    status        TEXT        NOT NULL,       -- 'IN_FLIGHT', 'SUCCEEDED', 'FAILED'
    response_code INT,
    response_body JSONB,
    transfer_id   BIGINT REFERENCES transfer (id),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Transactional outbox.
CREATE TABLE outbox (
    id         BIGSERIAL PRIMARY KEY,
    aggregate  TEXT        NOT NULL,
    event_type TEXT        NOT NULL,
    payload    JSONB       NOT NULL,
    published  BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_outbox_unpublished ON outbox (id) WHERE published = FALSE;

-- Saga state for two-sided trades so a restart can resume/compensate deterministically.
CREATE TABLE trade_saga (
    id          BIGSERIAL PRIMARY KEY,
    external_id UUID        NOT NULL,
    state       TEXT        NOT NULL,          -- STARTED, ESCROWED, CROSSED, COMPLETED, COMPENSATING, COMPENSATED, FAILED
    payload     JSONB       NOT NULL,          -- serialized trade request
    last_error  TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_trade_saga_external_id UNIQUE (external_id)
);

CREATE INDEX idx_trade_saga_state ON trade_saga (state);

-- Audit log: every transfer records who/what/when/why.
CREATE TABLE audit_log (
    id          BIGSERIAL PRIMARY KEY,
    external_id UUID,
    actor       TEXT        NOT NULL,
    action      TEXT        NOT NULL,
    detail      JSONB,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_external ON audit_log (external_id);
