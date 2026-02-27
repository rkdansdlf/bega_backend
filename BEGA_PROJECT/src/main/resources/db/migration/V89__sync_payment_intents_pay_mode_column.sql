-- V89: Align payment_intents pay_mode column with shared JPA mapping.
-- Some environments may have introduced MODE; Oracle reserves MODE as keyword.

DECLARE
    v_has_table NUMBER := 0;
    v_has_pay_mode NUMBER := 0;
    v_has_mode NUMBER := 0;
BEGIN
    SELECT COUNT(*)
      INTO v_has_table
      FROM user_tables
     WHERE table_name = 'PAYMENT_INTENTS';

    IF v_has_table = 0 THEN
        RETURN;
    END IF;

    SELECT COUNT(*)
      INTO v_has_pay_mode
      FROM user_tab_cols
     WHERE table_name = 'PAYMENT_INTENTS'
       AND column_name = 'PAY_MODE';

    IF v_has_pay_mode = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE payment_intents ADD (pay_mode VARCHAR2(20))';
        v_has_pay_mode := 1;
    END IF;

    SELECT COUNT(*)
      INTO v_has_mode
      FROM user_tab_cols
     WHERE table_name = 'PAYMENT_INTENTS'
       AND column_name = 'MODE';

    IF v_has_mode = 1 THEN
        EXECUTE IMMEDIATE 'UPDATE payment_intents SET pay_mode = "MODE" WHERE pay_mode IS NULL AND "MODE" IS NOT NULL';
    END IF;

    EXECUTE IMMEDIATE 'UPDATE payment_intents SET pay_mode = ''PREPARED'' WHERE pay_mode IS NULL';

    BEGIN
        EXECUTE IMMEDIATE 'ALTER TABLE payment_intents MODIFY (pay_mode NOT NULL)';
    EXCEPTION
        WHEN OTHERS THEN
            NULL;
    END;
END;
/
