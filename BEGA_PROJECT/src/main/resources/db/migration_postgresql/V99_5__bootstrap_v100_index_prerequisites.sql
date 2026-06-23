-- Bootstrap objects referenced by V100 performance indexes on fresh PostgreSQL.
-- Existing environments are left unchanged via IF NOT EXISTS guards.

CREATE TABLE IF NOT EXISTS notifications (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    type VARCHAR(50) NOT NULL DEFAULT 'NEW_DEVICE_LOGIN',
    title VARCHAR(200) NOT NULL DEFAULT '',
    message VARCHAR(500) NOT NULL DEFAULT '',
    related_id BIGINT,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    createdat TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE notifications
    ADD COLUMN IF NOT EXISTS type VARCHAR(50) NOT NULL DEFAULT 'NEW_DEVICE_LOGIN';

ALTER TABLE notifications
    ADD COLUMN IF NOT EXISTS title VARCHAR(200) NOT NULL DEFAULT '';

ALTER TABLE notifications
    ADD COLUMN IF NOT EXISTS message VARCHAR(500) NOT NULL DEFAULT '';

ALTER TABLE notifications
    ADD COLUMN IF NOT EXISTS related_id BIGINT;

ALTER TABLE notifications
    ADD COLUMN IF NOT EXISTS createdat TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE parties
    ADD COLUMN IF NOT EXISTS game_date DATE;

UPDATE parties
SET game_date = gamedate
WHERE game_date IS NULL
  AND gamedate IS NOT NULL;

CREATE TABLE IF NOT EXISTS cheer_posts (
    id BIGSERIAL PRIMARY KEY,
    author_id BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
