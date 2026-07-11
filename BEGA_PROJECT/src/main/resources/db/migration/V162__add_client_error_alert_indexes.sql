-- V162: Add composite indexes for client error alert evaluation on Oracle.

DECLARE
    v_index_count NUMBER;
    e_index_exists EXCEPTION;
    PRAGMA EXCEPTION_INIT(e_index_exists, -955);
    e_already_indexed EXCEPTION;
    PRAGMA EXCEPTION_INIT(e_already_indexed, -1408);
BEGIN
    SELECT COUNT(*)
      INTO v_index_count
      FROM user_indexes
     WHERE table_name = 'CLIENT_ERROR_EVENTS'
       AND index_name = 'IDX_CLIENT_ERROR_EVENTS_ALERT_WINDOW';

    IF v_index_count = 0 THEN
        BEGIN
            EXECUTE IMMEDIATE
                'CREATE INDEX idx_client_error_events_alert_window
                    ON client_error_events(occurred_at DESC, fingerprint, bucket)';
        EXCEPTION
            WHEN e_index_exists OR e_already_indexed THEN
                NULL;
        END;
    END IF;
END;
/

DECLARE
    v_index_count NUMBER;
    e_index_exists EXCEPTION;
    PRAGMA EXCEPTION_INIT(e_index_exists, -955);
    e_already_indexed EXCEPTION;
    PRAGMA EXCEPTION_INIT(e_already_indexed, -1408);
BEGIN
    SELECT COUNT(*)
      INTO v_index_count
      FROM user_indexes
     WHERE table_name = 'CLIENT_ERROR_EVENTS'
       AND index_name = 'IDX_CLIENT_ERROR_EVENTS_FP_OCCURRED';

    IF v_index_count = 0 THEN
        BEGIN
            EXECUTE IMMEDIATE
                'CREATE INDEX idx_client_error_events_fp_occurred
                    ON client_error_events(fingerprint, occurred_at DESC)';
        EXCEPTION
            WHEN e_index_exists OR e_already_indexed THEN
                NULL;
        END;
    END IF;
END;
/

DECLARE
    v_index_count NUMBER;
    e_index_exists EXCEPTION;
    PRAGMA EXCEPTION_INIT(e_index_exists, -955);
    e_already_indexed EXCEPTION;
    PRAGMA EXCEPTION_INIT(e_already_indexed, -1408);
BEGIN
    SELECT COUNT(*)
      INTO v_index_count
      FROM user_indexes
     WHERE table_name = 'CLIENT_ERROR_ALERT_NOTIFICATIONS'
       AND index_name = 'IDX_CLIENT_ERROR_ALERT_NOTIF_COOLDOWN';

    IF v_index_count = 0 THEN
        BEGIN
            EXECUTE IMMEDIATE
                'CREATE INDEX idx_client_error_alert_notif_cooldown
                    ON client_error_alert_notifications(fingerprint, notified_at DESC)';
        EXCEPTION
            WHEN e_index_exists OR e_already_indexed THEN
                NULL;
        END;
    END IF;
END;
/
