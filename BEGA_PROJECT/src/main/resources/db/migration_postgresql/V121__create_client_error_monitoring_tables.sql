CREATE TABLE IF NOT EXISTS client_error_events (
    id BIGSERIAL PRIMARY KEY,
    event_id VARCHAR(64) NOT NULL,
    bucket VARCHAR(20) NOT NULL,
    source VARCHAR(40) NOT NULL,
    message VARCHAR(1000) NOT NULL,
    stack_trace TEXT NULL,
    component_stack TEXT NULL,
    route VARCHAR(500) NOT NULL,
    normalized_route VARCHAR(500) NOT NULL,
    status_code INTEGER NULL,
    status_group VARCHAR(8) NOT NULL DEFAULT 'none',
    response_code VARCHAR(64) NULL,
    method VARCHAR(16) NULL,
    endpoint VARCHAR(500) NULL,
    normalized_endpoint VARCHAR(500) NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    session_id VARCHAR(128) NULL,
    user_id BIGINT NULL,
    fingerprint VARCHAR(64) NOT NULL,
    feedback_count INTEGER NOT NULL DEFAULT 0
);

ALTER TABLE client_error_events
    ADD CONSTRAINT uq_client_error_events_event_id UNIQUE (event_id);

CREATE INDEX IF NOT EXISTS idx_client_error_events_occurred_at
    ON client_error_events(occurred_at DESC);

CREATE INDEX IF NOT EXISTS idx_client_error_events_bucket_occurred_at
    ON client_error_events(bucket, occurred_at DESC);

CREATE INDEX IF NOT EXISTS idx_client_error_events_fingerprint
    ON client_error_events(fingerprint);

CREATE INDEX IF NOT EXISTS idx_client_error_events_normalized_route
    ON client_error_events(normalized_route);

CREATE TABLE IF NOT EXISTS client_error_feedback (
    id BIGSERIAL PRIMARY KEY,
    event_id VARCHAR(64) NOT NULL,
    comment TEXT NOT NULL,
    action_taken VARCHAR(64) NOT NULL,
    route VARCHAR(500) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_client_error_feedback_event_id
    ON client_error_feedback(event_id);

CREATE INDEX IF NOT EXISTS idx_client_error_feedback_occurred_at
    ON client_error_feedback(occurred_at DESC);

CREATE TABLE IF NOT EXISTS client_error_alert_notifications (
    id BIGSERIAL PRIMARY KEY,
    fingerprint VARCHAR(64) NOT NULL,
    bucket VARCHAR(20) NOT NULL,
    source VARCHAR(40) NOT NULL,
    route VARCHAR(500) NOT NULL,
    status_group VARCHAR(8) NOT NULL DEFAULT 'none',
    observed_count BIGINT NOT NULL,
    threshold_count INTEGER NOT NULL,
    window_minutes INTEGER NOT NULL,
    latest_event_id VARCHAR(64) NULL,
    latest_message VARCHAR(1000) NULL,
    latest_occurred_at TIMESTAMPTZ NULL,
    notified_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    delivery_status VARCHAR(20) NOT NULL,
    failure_reason VARCHAR(1000) NULL
);

CREATE INDEX IF NOT EXISTS idx_client_error_alert_notifications_fingerprint
    ON client_error_alert_notifications(fingerprint);

CREATE INDEX IF NOT EXISTS idx_client_error_alert_notifications_notified_at
    ON client_error_alert_notifications(notified_at DESC);
