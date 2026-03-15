-- V59: repost policy schema alignment (PostgreSQL)
-- - Add share/source metadata columns to cheer_post
-- - Add moderation workflow columns to cheer_post_reports

CREATE OR REPLACE FUNCTION __bega_exec_if_table_exists(target_table text, statement text)
RETURNS void AS $$
BEGIN
    IF to_regclass(target_table) IS NOT NULL THEN
        EXECUTE statement;
    END IF;
END;
$$ LANGUAGE plpgsql;

SELECT __bega_exec_if_table_exists('cheer_post', $sql$
ALTER TABLE cheer_post
    ADD COLUMN IF NOT EXISTS share_mode VARCHAR(24)
$sql$);

SELECT __bega_exec_if_table_exists('cheer_post', $sql$
ALTER TABLE cheer_post
    ADD COLUMN IF NOT EXISTS source_url VARCHAR(1024)
$sql$);

SELECT __bega_exec_if_table_exists('cheer_post', $sql$
ALTER TABLE cheer_post
    ADD COLUMN IF NOT EXISTS source_title VARCHAR(300)
$sql$);

SELECT __bega_exec_if_table_exists('cheer_post', $sql$
ALTER TABLE cheer_post
    ADD COLUMN IF NOT EXISTS source_author VARCHAR(200)
$sql$);

SELECT __bega_exec_if_table_exists('cheer_post', $sql$
ALTER TABLE cheer_post
    ADD COLUMN IF NOT EXISTS source_license VARCHAR(120)
$sql$);

SELECT __bega_exec_if_table_exists('cheer_post', $sql$
ALTER TABLE cheer_post
    ADD COLUMN IF NOT EXISTS source_license_url VARCHAR(1024)
$sql$);

SELECT __bega_exec_if_table_exists('cheer_post', $sql$
ALTER TABLE cheer_post
    ADD COLUMN IF NOT EXISTS source_changed_note VARCHAR(1200)
$sql$);

SELECT __bega_exec_if_table_exists('cheer_post', $sql$
ALTER TABLE cheer_post
    ADD COLUMN IF NOT EXISTS source_snapshot_type VARCHAR(80)
$sql$);

SELECT __bega_exec_if_table_exists('cheer_post', $sql$
UPDATE cheer_post
SET share_mode = 'INTERNAL_REPOST'
WHERE share_mode IS NULL
$sql$);

SELECT __bega_exec_if_table_exists('cheer_post', $sql$
ALTER TABLE cheer_post
    ALTER COLUMN share_mode SET DEFAULT 'INTERNAL_REPOST'
$sql$);

SELECT __bega_exec_if_table_exists('cheer_post_reports', $sql$
ALTER TABLE cheer_post_reports
    ADD COLUMN IF NOT EXISTS status VARCHAR(24)
$sql$);

SELECT __bega_exec_if_table_exists('cheer_post_reports', $sql$
ALTER TABLE cheer_post_reports
    ADD COLUMN IF NOT EXISTS admin_action VARCHAR(30)
$sql$);

SELECT __bega_exec_if_table_exists('cheer_post_reports', $sql$
ALTER TABLE cheer_post_reports
    ADD COLUMN IF NOT EXISTS admin_memo VARCHAR(1000)
$sql$);

SELECT __bega_exec_if_table_exists('cheer_post_reports', $sql$
ALTER TABLE cheer_post_reports
    ADD COLUMN IF NOT EXISTS handled_by BIGINT
$sql$);

SELECT __bega_exec_if_table_exists('cheer_post_reports', $sql$
ALTER TABLE cheer_post_reports
    ADD COLUMN IF NOT EXISTS handled_at TIMESTAMP
$sql$);

SELECT __bega_exec_if_table_exists('cheer_post_reports', $sql$
ALTER TABLE cheer_post_reports
    ADD COLUMN IF NOT EXISTS evidence_url VARCHAR(1024)
$sql$);

SELECT __bega_exec_if_table_exists('cheer_post_reports', $sql$
ALTER TABLE cheer_post_reports
    ADD COLUMN IF NOT EXISTS requested_action VARCHAR(64)
$sql$);

SELECT __bega_exec_if_table_exists('cheer_post_reports', $sql$
ALTER TABLE cheer_post_reports
    ADD COLUMN IF NOT EXISTS appeal_status VARCHAR(24)
$sql$);

SELECT __bega_exec_if_table_exists('cheer_post_reports', $sql$
ALTER TABLE cheer_post_reports
    ADD COLUMN IF NOT EXISTS appeal_reason VARCHAR(1200)
$sql$);

SELECT __bega_exec_if_table_exists('cheer_post_reports', $sql$
ALTER TABLE cheer_post_reports
    ADD COLUMN IF NOT EXISTS appeal_count INTEGER
$sql$);

SELECT __bega_exec_if_table_exists('cheer_post_reports', $sql$
UPDATE cheer_post_reports
SET status = 'PENDING'
WHERE status IS NULL
$sql$);

SELECT __bega_exec_if_table_exists('cheer_post_reports', $sql$
UPDATE cheer_post_reports
SET appeal_status = 'NONE'
WHERE appeal_status IS NULL
$sql$);

SELECT __bega_exec_if_table_exists('cheer_post_reports', $sql$
UPDATE cheer_post_reports
SET appeal_count = 0
WHERE appeal_count IS NULL
$sql$);

SELECT __bega_exec_if_table_exists('cheer_post', $sql$
CREATE INDEX IF NOT EXISTS idx_cheer_post_source_url ON cheer_post (source_url)
$sql$);

SELECT __bega_exec_if_table_exists('cheer_post_reports', $sql$
CREATE INDEX IF NOT EXISTS idx_cheer_post_reports_status_createdat ON cheer_post_reports (status, createdat DESC)
$sql$);

SELECT __bega_exec_if_table_exists('cheer_post_reports', $sql$
CREATE INDEX IF NOT EXISTS idx_cheer_post_reports_reporter_post_createdat ON cheer_post_reports (reporter_id, post_id, createdat DESC)
$sql$);

DROP FUNCTION IF EXISTS __bega_exec_if_table_exists(text, text);
