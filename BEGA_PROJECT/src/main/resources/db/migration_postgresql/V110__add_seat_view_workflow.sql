ALTER TABLE IF EXISTS bega_diary
    ADD COLUMN IF NOT EXISTS ticket_verified BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE IF EXISTS bega_diary
    ADD COLUMN IF NOT EXISTS ticket_verified_at TIMESTAMP;

CREATE TABLE IF NOT EXISTS seat_view_photo (
    id BIGSERIAL PRIMARY KEY,
    diary_id BIGINT NOT NULL REFERENCES bega_diary(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL,
    storage_path VARCHAR(2048) NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    ai_suggested_label VARCHAR(32),
    ai_confidence DOUBLE PRECISION,
    ai_reason VARCHAR(1000),
    user_selected BOOLEAN NOT NULL DEFAULT FALSE,
    moderation_status VARCHAR(32),
    admin_label VARCHAR(32),
    admin_memo VARCHAR(1000),
    reviewed_by BIGINT,
    reviewed_at TIMESTAMP,
    reward_granted BOOLEAN NOT NULL DEFAULT FALSE,
    createdat TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updatedat TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_seat_view_photo_diary_id
    ON seat_view_photo (diary_id);

CREATE INDEX IF NOT EXISTS idx_seat_view_photo_public
    ON seat_view_photo (moderation_status, admin_label, user_selected);
