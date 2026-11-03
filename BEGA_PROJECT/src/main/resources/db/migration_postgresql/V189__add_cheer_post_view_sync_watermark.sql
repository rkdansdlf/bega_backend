-- V189: Persist the last applied Redis view-sync batch sequence per post.
ALTER TABLE cheer_post
    ADD COLUMN IF NOT EXISTS view_sync_watermark BIGINT NOT NULL DEFAULT 0;
