DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*)
      INTO v_count
      FROM user_tab_cols
     WHERE table_name = 'CLIENT_ERROR_ALERT_NOTIFICATIONS'
       AND column_name = 'DELIVERY_CHANNEL';

    IF v_count = 0 THEN
        EXECUTE IMMEDIATE '
            ALTER TABLE client_error_alert_notifications
            ADD delivery_channel VARCHAR2(20 CHAR)';
    END IF;
END;
/

BEGIN
    EXECUTE IMMEDIATE '
        UPDATE client_error_alert_notifications
           SET delivery_channel = ''SLACK''
         WHERE delivery_channel IS NULL';
END;
/

BEGIN
    EXECUTE IMMEDIATE '
        ALTER TABLE client_error_alert_notifications
        MODIFY delivery_channel VARCHAR2(20 CHAR) NOT NULL';
END;
/
