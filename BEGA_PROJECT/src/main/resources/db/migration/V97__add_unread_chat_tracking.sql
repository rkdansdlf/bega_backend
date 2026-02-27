DECLARE
    v_column_count NUMBER := 0;
BEGIN
    SELECT COUNT(*)
      INTO v_column_count
      FROM user_tab_cols
     WHERE table_name = 'PARTIES'
       AND column_name = 'HOST_LAST_READ_CHAT_AT';

    IF v_column_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE parties ADD (host_last_read_chat_at TIMESTAMP(6) WITH TIME ZONE)';
    END IF;

    SELECT COUNT(*)
      INTO v_column_count
      FROM user_tab_cols
     WHERE table_name = 'PARTY_APPLICATIONS'
       AND column_name = 'LAST_READ_CHAT_AT';

    IF v_column_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE party_applications ADD (last_read_chat_at TIMESTAMP(6) WITH TIME ZONE)';
    END IF;
END;
/
