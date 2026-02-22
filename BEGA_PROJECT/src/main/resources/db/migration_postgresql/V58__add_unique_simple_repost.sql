-- V58: Ensure one active SIMPLE repost per author + target post (PostgreSQL).
-- Purpose:
-- - Prevent duplicate SIMPLE repost rows from concurrent or repeated requests.
-- - Keep existing behavior for QUOTE reposts and quote duplicates.

DO $$
DECLARE
    v_duplicate_count BIGINT;
BEGIN
    SELECT COUNT(*)
    INTO v_duplicate_count
    FROM (
        SELECT cp.author_id, cp.repost_of_id
        FROM cheer_post cp
        WHERE cp.repost_type = 'SIMPLE'
          AND cp.repost_of_id IS NOT NULL
          AND cp.deleted = FALSE
        GROUP BY cp.author_id, cp.repost_of_id
        HAVING COUNT(*) > 1
    ) existing_duplicates;

    IF v_duplicate_count > 0 THEN
        RAISE EXCEPTION 'Cannot apply V58: duplicate SIMPLE repost rows already exist.';
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uq_cheer_post_simple_repost
ON cheer_post (author_id, repost_of_id)
WHERE repost_type = 'SIMPLE' AND repost_of_id IS NOT NULL AND deleted = FALSE;
