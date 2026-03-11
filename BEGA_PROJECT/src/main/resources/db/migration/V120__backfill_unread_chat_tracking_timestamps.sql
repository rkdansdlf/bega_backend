DECLARE
    v_count NUMBER := 0;
BEGIN
    SELECT COUNT(*)
      INTO v_count
      FROM user_tab_cols
     WHERE table_name = 'PARTIES'
       AND column_name = 'HOST_LAST_READ_CHAT_AT';

    IF v_count > 0 THEN
        EXECUTE IMMEDIATE '
            UPDATE parties
               SET host_last_read_chat_at = COALESCE(host_last_read_chat_at, createdat, SYSTIMESTAMP)
             WHERE host_last_read_chat_at IS NULL
        ';
    END IF;

    SELECT COUNT(*)
      INTO v_count
      FROM user_tab_cols
     WHERE table_name = 'PARTY_APPLICATIONS'
       AND column_name = 'LAST_READ_CHAT_AT';

    IF v_count > 0 THEN
        EXECUTE IMMEDIATE '
            UPDATE party_applications
               SET last_read_chat_at = COALESCE(last_read_chat_at, created_at, SYSTIMESTAMP)
             WHERE last_read_chat_at IS NULL
        ';
    END IF;
END;
/
