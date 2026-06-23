DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*)
      INTO v_count
      FROM user_tab_columns
     WHERE table_name = 'PARTIES'
       AND column_name = 'RESERVATION_DEPOSIT_AMOUNT';

    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE parties ADD (reservation_deposit_amount NUMBER(10))';
    END IF;
END;
/

COMMENT ON COLUMN parties.reservation_deposit_amount IS 'Optional per-person reservation deposit shown on mate party detail';
