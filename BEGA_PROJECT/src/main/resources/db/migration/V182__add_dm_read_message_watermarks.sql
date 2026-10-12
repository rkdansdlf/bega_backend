DECLARE
    v_col_count NUMBER := 0;
BEGIN
    SELECT COUNT(*)
      INTO v_col_count
      FROM user_tab_columns
     WHERE table_name = 'DM_ROOMS'
       AND column_name = 'PARTICIPANT_ONE_LAST_READ_MESSAGE_ID';

    IF v_col_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE dm_rooms ADD participant_one_last_read_message_id NUMBER(19)';
    END IF;
END;
/

DECLARE
    v_col_count NUMBER := 0;
BEGIN
    SELECT COUNT(*)
      INTO v_col_count
      FROM user_tab_columns
     WHERE table_name = 'DM_ROOMS'
       AND column_name = 'PARTICIPANT_TWO_LAST_READ_MESSAGE_ID';

    IF v_col_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE dm_rooms ADD participant_two_last_read_message_id NUMBER(19)';
    END IF;
END;
/
