DECLARE
    v_column_exists NUMBER := 0;
BEGIN
    SELECT COUNT(*)
      INTO v_column_exists
      FROM user_tab_cols
     WHERE table_name = 'PARTIES'
       AND column_name = 'SEAT_DETAIL';

    IF v_column_exists = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE parties ADD (seat_detail VARCHAR2(100))';
    END IF;
END;
/
