-- V170: Add composite indexes for client error admin filter queries on PostgreSQL.

CREATE INDEX IF NOT EXISTS idx_client_error_events_source_occurred
    ON client_error_events (source, occurred_at DESC);

CREATE INDEX IF NOT EXISTS idx_client_error_events_status_occurred
    ON client_error_events (status_group, occurred_at DESC);

CREATE INDEX IF NOT EXISTS idx_client_error_events_occurred_id
    ON client_error_events (occurred_at DESC, id DESC);
