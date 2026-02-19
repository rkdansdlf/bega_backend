-- V59: repost policy schema alignment (PostgreSQL)
-- - Add share/source metadata columns to cheer_post
-- - Add moderation workflow columns to cheer_post_reports

ALTER TABLE cheer_post
    ADD COLUMN IF NOT EXISTS share_mode VARCHAR(24);

ALTER TABLE cheer_post
    ADD COLUMN IF NOT EXISTS source_url VARCHAR(1024);

ALTER TABLE cheer_post
    ADD COLUMN IF NOT EXISTS source_title VARCHAR(300);

ALTER TABLE cheer_post
    ADD COLUMN IF NOT EXISTS source_author VARCHAR(200);

ALTER TABLE cheer_post
    ADD COLUMN IF NOT EXISTS source_license VARCHAR(120);

ALTER TABLE cheer_post
    ADD COLUMN IF NOT EXISTS source_license_url VARCHAR(1024);

ALTER TABLE cheer_post
    ADD COLUMN IF NOT EXISTS source_changed_note VARCHAR(1200);

ALTER TABLE cheer_post
    ADD COLUMN IF NOT EXISTS source_snapshot_type VARCHAR(80);

UPDATE cheer_post
SET share_mode = 'INTERNAL_REPOST'
WHERE share_mode IS NULL;

ALTER TABLE cheer_post
    ALTER COLUMN share_mode SET DEFAULT 'INTERNAL_REPOST';

ALTER TABLE cheer_post_reports
    ADD COLUMN IF NOT EXISTS status VARCHAR(24);

ALTER TABLE cheer_post_reports
    ADD COLUMN IF NOT EXISTS admin_action VARCHAR(30);

ALTER TABLE cheer_post_reports
    ADD COLUMN IF NOT EXISTS admin_memo VARCHAR(1000);

ALTER TABLE cheer_post_reports
    ADD COLUMN IF NOT EXISTS handled_by BIGINT;

ALTER TABLE cheer_post_reports
    ADD COLUMN IF NOT EXISTS handled_at TIMESTAMP;

ALTER TABLE cheer_post_reports
    ADD COLUMN IF NOT EXISTS evidence_url VARCHAR(1024);

ALTER TABLE cheer_post_reports
    ADD COLUMN IF NOT EXISTS requested_action VARCHAR(64);

ALTER TABLE cheer_post_reports
    ADD COLUMN IF NOT EXISTS appeal_status VARCHAR(24);

ALTER TABLE cheer_post_reports
    ADD COLUMN IF NOT EXISTS appeal_reason VARCHAR(1200);

ALTER TABLE cheer_post_reports
    ADD COLUMN IF NOT EXISTS appeal_count INTEGER;

UPDATE cheer_post_reports
SET status = 'PENDING'
WHERE status IS NULL;

UPDATE cheer_post_reports
SET appeal_status = 'NONE'
WHERE appeal_status IS NULL;

UPDATE cheer_post_reports
SET appeal_count = 0
WHERE appeal_count IS NULL;

CREATE INDEX IF NOT EXISTS idx_cheer_post_source_url ON cheer_post (source_url);
CREATE INDEX IF NOT EXISTS idx_cheer_post_reports_status_createdat ON cheer_post_reports (status, createdat DESC);
CREATE INDEX IF NOT EXISTS idx_cheer_post_reports_reporter_post_createdat ON cheer_post_reports (reporter_id, post_id, createdat DESC);
