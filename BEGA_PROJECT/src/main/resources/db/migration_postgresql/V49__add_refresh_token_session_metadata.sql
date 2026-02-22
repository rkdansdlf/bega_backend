-- V49: Add session metadata columns to refresh token table
ALTER TABLE refresh_tokens
    ADD COLUMN IF NOT EXISTS device_type VARCHAR(32);

ALTER TABLE refresh_tokens
    ADD COLUMN IF NOT EXISTS device_label VARCHAR(255);

ALTER TABLE refresh_tokens
    ADD COLUMN IF NOT EXISTS browser VARCHAR(64);

ALTER TABLE refresh_tokens
    ADD COLUMN IF NOT EXISTS os VARCHAR(64);

ALTER TABLE refresh_tokens
    ADD COLUMN IF NOT EXISTS ip VARCHAR(64);

ALTER TABLE refresh_tokens
    ADD COLUMN IF NOT EXISTS last_seen_at TIMESTAMP;
