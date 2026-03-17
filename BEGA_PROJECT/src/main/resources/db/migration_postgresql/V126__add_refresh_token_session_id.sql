-- V126: Add stable session identifiers to refresh tokens (PostgreSQL)

ALTER TABLE refresh_tokens
    ADD COLUMN IF NOT EXISTS session_id VARCHAR(64);

CREATE UNIQUE INDEX IF NOT EXISTS uq_refresh_tokens_session_id
    ON refresh_tokens(session_id);
