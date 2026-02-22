-- V58: Add unique indexes to party_applications and extend payment_intents (Oracle)

DECLARE
    e_col_exists EXCEPTION;
    PRAGMA EXCEPTION_INIT(e_col_exists, -1430); -- ORA-01430
BEGIN
    BEGIN
        EXECUTE IMMEDIATE 'ALTER TABLE payment_intents ADD (flow_type VARCHAR2(30))';
    EXCEPTION
        WHEN e_col_exists THEN NULL;
    END;

    BEGIN
        EXECUTE IMMEDIATE 'ALTER TABLE payment_intents ADD (cancel_policy_version VARCHAR2(50))';
    EXCEPTION
        WHEN e_col_exists THEN NULL;
    END;
END;
/

UPDATE payment_intents
SET flow_type = CASE
    WHEN payment_type = 'FULL' THEN 'SELLING_FULL'
    ELSE 'DEPOSIT'
END
WHERE flow_type IS NULL;

BEGIN
    EXECUTE IMMEDIATE 'ALTER TABLE payment_intents MODIFY (flow_type NOT NULL)';
EXCEPTION
    WHEN OTHERS THEN
        NULL;
END;
/

DECLARE
    e_index_exists EXCEPTION;
    PRAGMA EXCEPTION_INIT(e_index_exists, -955); -- ORA-00955
BEGIN
    BEGIN
        EXECUTE IMMEDIATE 'CREATE UNIQUE INDEX uq_party_applications_order_id ON party_applications(order_id)';
    EXCEPTION
        WHEN e_index_exists THEN NULL;
    END;

    BEGIN
        EXECUTE IMMEDIATE 'CREATE UNIQUE INDEX uq_party_applications_payment_key ON party_applications(payment_key)';
    EXCEPTION
        WHEN e_index_exists THEN NULL;
    END;
END;
/
