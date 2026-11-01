ALTER TABLE media_assets
    ADD COLUMN IF NOT EXISTS finalize_token VARCHAR(36),
    ADD COLUMN IF NOT EXISTS finalize_lease_until TIMESTAMP,
    ADD COLUMN IF NOT EXISTS finalize_object_key VARCHAR(2048),
    ADD COLUMN IF NOT EXISTS finalize_feed_object_key VARCHAR(2048),
    ADD COLUMN IF NOT EXISTS finalize_cleanup_verified_at TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_media_asset_finalize_lease
    ON media_assets (status, finalize_lease_until, upload_expires_at);

CREATE TABLE IF NOT EXISTS media_finalize_attempts (
    attempt_id VARCHAR(36) PRIMARY KEY,
    media_asset_id BIGINT NOT NULL REFERENCES media_assets(id),
    media_domain VARCHAR(30) NOT NULL,
    bucket_name VARCHAR(255) NOT NULL,
    object_key VARCHAR(2048),
    feed_object_key VARCHAR(2048),
    state VARCHAR(32) NOT NULL,
    lease_expires_at TIMESTAMP,
    next_cleanup_at TIMESTAMP,
    cleanup_token VARCHAR(36),
    cleanup_lease_until TIMESTAMP,
    cleanup_attempt_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_mfa_cleanup_due
    ON media_finalize_attempts (state, next_cleanup_at, cleanup_lease_until, attempt_id);

CREATE INDEX IF NOT EXISTS idx_mfa_asset_state
    ON media_finalize_attempts (media_asset_id, state, created_at, attempt_id);
