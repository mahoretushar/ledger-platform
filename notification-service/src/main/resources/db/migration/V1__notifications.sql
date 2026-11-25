CREATE TABLE notification (
    id           UUID PRIMARY KEY,
    event_id     UUID           NOT NULL,
    recipient    VARCHAR(64)    NOT NULL,
    account_id   UUID           NOT NULL,
    transfer_id  UUID           NOT NULL,
    direction    VARCHAR(6)     NOT NULL,
    amount       NUMERIC(19, 4) NOT NULL,
    currency     VARCHAR(3)     NOT NULL,
    message      VARCHAR(300)   NOT NULL,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    -- one notification per event per recipient: replays and duplicates are absorbed here
    CONSTRAINT uk_notification_event_recipient UNIQUE (event_id, recipient)
);
CREATE INDEX idx_notification_recipient ON notification (recipient, created_at DESC);
