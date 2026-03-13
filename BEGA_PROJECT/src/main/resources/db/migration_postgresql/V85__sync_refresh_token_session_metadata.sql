-- V85: Ensure refresh_tokens session metadata columns exist.
-- PostgreSQL equivalent of Oracle V85.
-- V49 and V52 already added these columns; this migration is a no-op on existing envs.

ALTER TABLE IF EXISTS refresh_tokens
    ADD COLUMN IF NOT EXISTS device_type VARCHAR(32);

ALTER TABLE IF EXISTS refresh_tokens
    ADD COLUMN IF NOT EXISTS device_label VARCHAR(255);

ALTER TABLE IF EXISTS refresh_tokens
    ADD COLUMN IF NOT EXISTS browser VARCHAR(64);

ALTER TABLE IF EXISTS refresh_tokens
    ADD COLUMN IF NOT EXISTS os VARCHAR(64);

ALTER TABLE IF EXISTS refresh_tokens
    ADD COLUMN IF NOT EXISTS ip VARCHAR(64);

ALTER TABLE IF EXISTS refresh_tokens
    ADD COLUMN IF NOT EXISTS last_seen_at TIMESTAMP;
