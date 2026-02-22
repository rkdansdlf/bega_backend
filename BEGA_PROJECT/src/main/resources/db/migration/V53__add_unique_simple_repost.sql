-- V53: Ensure one active SIMPLE repost per author + target post (Oracle).
-- Purpose:
-- - Prevent duplicate SIMPLE repost rows from concurrent or repeated requests.
-- - Keep existing behavior for QUOTE reposts and quote duplicates.

DECLARE
    v_duplicate_count NUMBER;
BEGIN
    SELECT COUNT(*)
      INTO v_duplicate_count
      FROM (
            SELECT cp.author_id, cp.repost_of_id
              FROM cheer_post cp
             WHERE cp.repost_type = 'SIMPLE'
               AND cp.repost_of_id IS NOT NULL
               AND NVL(cp.deleted, 0) = 0
             GROUP BY cp.author_id, cp.repost_of_id
            HAVING COUNT(*) > 1
      );

    IF v_duplicate_count > 0 THEN
        RAISE_APPLICATION_ERROR(-20001, 'Cannot apply V53: duplicate SIMPLE repost rows already exist.');
    END IF;
END;
/

DECLARE
    e_index_name_exists EXCEPTION;
    PRAGMA EXCEPTION_INIT(e_index_name_exists, -955);  -- ORA-00955
    e_column_list_indexed EXCEPTION;
    PRAGMA EXCEPTION_INIT(e_column_list_indexed, -1408); -- ORA-01408
BEGIN
    EXECUTE IMMEDIATE q'[
        CREATE UNIQUE INDEX uq_cheer_post_simple_repost
        ON cheer_post (
            CASE
                WHEN repost_type = 'SIMPLE'
                  AND repost_of_id IS NOT NULL
                  AND NVL(deleted, 0) = 0
                THEN author_id
            END,
            CASE
                WHEN repost_type = 'SIMPLE'
                  AND repost_of_id IS NOT NULL
                  AND NVL(deleted, 0) = 0
                THEN repost_of_id
            END
        )
    ]';
EXCEPTION
    WHEN e_index_name_exists OR e_column_list_indexed THEN
        NULL;
END;
/
