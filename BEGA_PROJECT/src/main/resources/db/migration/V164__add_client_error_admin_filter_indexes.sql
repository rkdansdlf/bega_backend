-- V164: Add composite indexes for client error admin filter queries on Oracle.

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
       AND index_name = 'IDX_CLIENT_ERROR_EVENTS_SOURCE_OCCURRED';

    IF v_index_count = 0 THEN
        BEGIN
            EXECUTE IMMEDIATE
                'CREATE INDEX idx_client_error_events_source_occurred
                    ON client_error_events(source, occurred_at DESC)';
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
       AND index_name = 'IDX_CLIENT_ERROR_EVENTS_OCCURRED_ID';

    IF v_index_count = 0 THEN
        BEGIN
            EXECUTE IMMEDIATE
                'CREATE INDEX idx_client_error_events_occurred_id
                    ON client_error_events(occurred_at DESC, id DESC)';
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
       AND index_name = 'IDX_CLIENT_ERROR_EVENTS_STATUS_OCCURRED';

    IF v_index_count = 0 THEN
        BEGIN
            EXECUTE IMMEDIATE
                'CREATE INDEX idx_client_error_events_status_occurred
                    ON client_error_events(status_group, occurred_at DESC)';
        EXCEPTION
            WHEN e_index_exists OR e_already_indexed THEN
                NULL;
        END;
    END IF;
END;
/
