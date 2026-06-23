-- V154: Add read-tracking columns to dm_rooms for DM Inbox unread badge support.
-- Adds participant_one_last_read_at / participant_two_last_read_at and inbox query indexes.
DECLARE
    v_col_count NUMBER := 0;
BEGIN
    SELECT COUNT(*)
      INTO v_col_count
      FROM user_tab_columns
     WHERE table_name = 'DM_ROOMS'
       AND column_name = 'PARTICIPANT_ONE_LAST_READ_AT';

    IF v_col_count = 0 THEN
        EXECUTE IMMEDIATE q'[
            ALTER TABLE dm_rooms ADD (
                participant_one_last_read_at TIMESTAMP(6) WITH TIME ZONE,
                participant_two_last_read_at TIMESTAMP(6) WITH TIME ZONE
            )
        ]';
    END IF;
END;
/

DECLARE
    e_index_exists EXCEPTION;
    PRAGMA EXCEPTION_INIT(e_index_exists, -955);
BEGIN
    BEGIN
        EXECUTE IMMEDIATE q'[
            CREATE INDEX idx_dm_rooms_participant_one
                ON dm_rooms (participant_one_id, created_at)
        ]';
    EXCEPTION
        WHEN e_index_exists THEN NULL;
    END;
END;
/

DECLARE
    e_index_exists EXCEPTION;
    PRAGMA EXCEPTION_INIT(e_index_exists, -955);
BEGIN
    BEGIN
        EXECUTE IMMEDIATE q'[
            CREATE INDEX idx_dm_rooms_participant_two
                ON dm_rooms (participant_two_id, created_at)
        ]';
    EXCEPTION
        WHEN e_index_exists THEN NULL;
    END;
END;
/
