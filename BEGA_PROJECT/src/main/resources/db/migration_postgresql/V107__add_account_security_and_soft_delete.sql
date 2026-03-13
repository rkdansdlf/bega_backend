-- V107: Add account security events, trusted devices, and soft delete metadata (PostgreSQL)

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS pending_deletion BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS deletion_requested_at TIMESTAMPTZ NULL;

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS deletion_scheduled_for TIMESTAMPTZ NULL;

CREATE TABLE IF NOT EXISTS trusted_devices (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    fingerprint VARCHAR(128) NOT NULL,
    device_label VARCHAR(255) NOT NULL,
    device_type VARCHAR(32) NOT NULL,
    browser VARCHAR(64) NOT NULL,
    os VARCHAR(64) NOT NULL,
    first_seen_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_login_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_ip VARCHAR(64),
    revoked_at TIMESTAMPTZ NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_trusted_devices_user_fingerprint
    ON trusted_devices(user_id, fingerprint);

CREATE INDEX IF NOT EXISTS ix_trusted_devices_user_last_seen
    ON trusted_devices(user_id, last_seen_at DESC);

CREATE TABLE IF NOT EXISTS account_security_events (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    event_type VARCHAR(64) NOT NULL,
    device_label VARCHAR(255),
    device_type VARCHAR(32),
    browser VARCHAR(64),
    os VARCHAR(64),
    ip VARCHAR(64),
    message VARCHAR(500) NOT NULL
);

CREATE INDEX IF NOT EXISTS ix_account_security_events_user_occurred
    ON account_security_events(user_id, occurred_at DESC);

CREATE TABLE IF NOT EXISTS account_deletion_tokens (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token VARCHAR(128) NOT NULL UNIQUE,
    expiry_date TIMESTAMPTZ NOT NULL,
    used BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS ix_account_deletion_tokens_user_id
    ON account_deletion_tokens(user_id);
