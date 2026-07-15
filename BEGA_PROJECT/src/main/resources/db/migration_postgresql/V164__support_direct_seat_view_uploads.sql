ALTER TABLE IF EXISTS seat_view_photo
    ALTER COLUMN diary_id DROP NOT NULL;

ALTER TABLE IF EXISTS seat_view_photo
    ADD COLUMN IF NOT EXISTS stadium VARCHAR(100),
    ADD COLUMN IF NOT EXISTS section VARCHAR(100),
    ADD COLUMN IF NOT EXISTS block VARCHAR(100),
    ADD COLUMN IF NOT EXISTS seat_row VARCHAR(100),
    ADD COLUMN IF NOT EXISTS seat_number VARCHAR(100),
    ADD COLUMN IF NOT EXISTS rating INTEGER,
    ADD COLUMN IF NOT EXISTS comment_text VARCHAR(140),
    ADD COLUMN IF NOT EXISTS tags_json VARCHAR(1000);

CREATE INDEX IF NOT EXISTS idx_seat_view_photo_direct
    ON seat_view_photo (stadium, section, moderation_status, admin_label, user_selected);
