-- V87: Add failure_code to payout_transactions (Oracle)

DECLARE
    e_col_exists EXCEPTION;
    PRAGMA EXCEPTION_INIT(e_col_exists, -1430);
BEGIN
    EXECUTE IMMEDIATE 'ALTER TABLE payout_transactions ADD (failure_code VARCHAR2(100))';
EXCEPTION
    WHEN e_col_exists THEN
        NULL;
END;
/

