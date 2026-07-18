CREATE TABLE IF NOT EXISTS realtime_outbox_events (
    id BIGSERIAL PRIMARY KEY,
    envelope_version INTEGER NOT NULL,
    event_id VARCHAR(128) NOT NULL,
    target VARCHAR(20) NOT NULL,
    destination VARCHAR(255) NOT NULL,
    user_id VARCHAR(128),
    payload TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    available_at TIMESTAMPTZ NOT NULL,
    locked_by VARCHAR(128),
    locked_until TIMESTAMPTZ,
    last_error VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_rt_outbox_event
    ON realtime_outbox_events(event_id);

CREATE INDEX IF NOT EXISTS idx_rt_outbox_due
    ON realtime_outbox_events(status, available_at, id);

CREATE INDEX IF NOT EXISTS idx_rt_outbox_lease
    ON realtime_outbox_events(status, locked_until, id);

CREATE INDEX IF NOT EXISTS idx_rt_outbox_cleanup
    ON realtime_outbox_events(status, published_at, id);
