-- V65: Add payout retry metadata columns (Oracle)

DECLARE
    e_col_exists EXCEPTION;
    PRAGMA EXCEPTION_INIT(e_col_exists, -1430);
BEGIN
    BEGIN
        EXECUTE IMMEDIATE 'ALTER TABLE payout_transactions ADD (retry_count NUMBER(10) DEFAULT 0 NOT NULL)';
    EXCEPTION
        WHEN e_col_exists THEN
            NULL;
    END;

    BEGIN
        EXECUTE IMMEDIATE 'ALTER TABLE payout_transactions ADD (last_retry_at TIMESTAMP(6) WITH TIME ZONE)';
    EXCEPTION
        WHEN e_col_exists THEN
            NULL;
    END;

    BEGIN
        EXECUTE IMMEDIATE 'ALTER TABLE payout_transactions ADD (next_retry_at TIMESTAMP(6) WITH TIME ZONE)';
    EXCEPTION
        WHEN e_col_exists THEN
            NULL;
    END;
END;
/

BEGIN
    EXECUTE IMMEDIATE 'UPDATE payout_transactions SET retry_count = 0 WHERE retry_count IS NULL';
EXCEPTION
    WHEN OTHERS THEN
        NULL;
END;
/
