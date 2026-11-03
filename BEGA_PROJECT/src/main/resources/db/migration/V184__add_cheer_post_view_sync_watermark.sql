-- V184: Persist the last applied Redis view-sync batch sequence per post.

DECLARE
    e_col_exists EXCEPTION;
    PRAGMA EXCEPTION_INIT(e_col_exists, -1430);
BEGIN
    EXECUTE IMMEDIATE 'ALTER TABLE cheer_post ADD (view_sync_watermark NUMBER(19,0) DEFAULT 0 NOT NULL)';
EXCEPTION
    WHEN e_col_exists THEN
        NULL;
END;
/
