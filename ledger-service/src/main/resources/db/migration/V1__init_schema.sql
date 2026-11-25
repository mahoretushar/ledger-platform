CREATE TABLE account (
    id          UUID PRIMARY KEY,
    owner_name  VARCHAR(120)   NOT NULL,
    currency    VARCHAR(3)       NOT NULL,
    balance     NUMERIC(19, 4) NOT NULL CHECK (balance >= 0),
    status      VARCHAR(16)    NOT NULL,
    version     BIGINT         NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE transfer (
    id               UUID PRIMARY KEY,
    idempotency_key  VARCHAR(80)    NOT NULL,
    request_hash     VARCHAR(64)    NOT NULL,
    from_account_id  UUID           NOT NULL REFERENCES account (id),
    to_account_id    UUID           NOT NULL REFERENCES account (id),
    amount           NUMERIC(19, 4) NOT NULL CHECK (amount > 0),
    currency         VARCHAR(3)       NOT NULL,
    status           VARCHAR(16)    NOT NULL,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_transfer_idempotency UNIQUE (idempotency_key)
);

CREATE TABLE ledger_entry (
    id             UUID PRIMARY KEY,
    transfer_id    UUID           NOT NULL REFERENCES transfer (id),
    account_id     UUID           NOT NULL REFERENCES account (id),
    direction      VARCHAR(6)     NOT NULL,
    amount         NUMERIC(19, 4) NOT NULL,
    balance_after  NUMERIC(19, 4) NOT NULL,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_ledger_entry_account ON ledger_entry (account_id, created_at);

CREATE TABLE outbox_event (
    id            UUID PRIMARY KEY,
    aggregate_id  UUID         NOT NULL,
    event_type    VARCHAR(60)  NOT NULL,
    payload       TEXT         NOT NULL,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    published_at  TIMESTAMP WITH TIME ZONE
);
CREATE INDEX idx_outbox_unpublished ON outbox_event (published_at, created_at);
