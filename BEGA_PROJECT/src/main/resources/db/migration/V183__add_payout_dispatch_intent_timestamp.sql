-- V183: Persist the party-expiry/provider-dispatch linearization point (Oracle)

DECLARE
    e_col_exists EXCEPTION;
    PRAGMA EXCEPTION_INIT(e_col_exists, -1430);
BEGIN
    EXECUTE IMMEDIATE 'ALTER TABLE payout_transactions ADD (provider_dispatch_intent_at TIMESTAMP(6) WITH TIME ZONE)';
EXCEPTION
    WHEN e_col_exists THEN
        NULL;
END;
/
