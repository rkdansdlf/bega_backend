ALTER TABLE cheer_post
    ADD COLUMN IF NOT EXISTS purge_started_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS purge_claim_token VARCHAR(36),
    ADD COLUMN IF NOT EXISTS purge_lease_until TIMESTAMP,
    ADD COLUMN IF NOT EXISTS purge_retry_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS purge_attempt_count INTEGER NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_cheer_post_purge_due
    ON cheer_post (deleted, purge_retry_at, purge_lease_until, id);
