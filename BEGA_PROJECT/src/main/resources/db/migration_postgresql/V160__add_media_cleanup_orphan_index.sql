CREATE INDEX IF NOT EXISTS idx_media_assets_status_created_id
    ON media_assets (status, created_at, id);
