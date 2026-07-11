-- V161: Add composite indexes for public mate party list filters (Oracle).

DECLARE
    v_count NUMBER;
    e_index_exists EXCEPTION;
    PRAGMA EXCEPTION_INIT(e_index_exists, -955);
    e_already_indexed EXCEPTION;
    PRAGMA EXCEPTION_INIT(e_already_indexed, -1408);
BEGIN
    SELECT COUNT(*)
      INTO v_count
      FROM user_indexes
     WHERE table_name = 'PARTIES'
       AND index_name = 'IDX_PARTIES_STATUS_CREATED_ID';

    IF v_count = 0 THEN
        BEGIN
            EXECUTE IMMEDIATE
                'CREATE INDEX idx_parties_status_created_id
                    ON parties(status, createdat DESC, id DESC)';
        EXCEPTION
            WHEN e_index_exists OR e_already_indexed THEN
                NULL;
        END;
    END IF;
END;
/

DECLARE
    v_count NUMBER;
    e_index_exists EXCEPTION;
    PRAGMA EXCEPTION_INIT(e_index_exists, -955);
    e_already_indexed EXCEPTION;
    PRAGMA EXCEPTION_INIT(e_already_indexed, -1408);
BEGIN
    SELECT COUNT(*)
      INTO v_count
      FROM user_indexes
     WHERE table_name = 'PARTIES'
       AND index_name = 'IDX_PARTIES_TEAM_STATUS_CREATED_ID';

    IF v_count = 0 THEN
        BEGIN
            EXECUTE IMMEDIATE
                'CREATE INDEX idx_parties_team_status_created_id
                    ON parties(teamid, status, createdat DESC, id DESC)';
        EXCEPTION
            WHEN e_index_exists OR e_already_indexed THEN
                NULL;
        END;
    END IF;
END;
/

DECLARE
    v_count NUMBER;
    e_index_exists EXCEPTION;
    PRAGMA EXCEPTION_INIT(e_index_exists, -955);
    e_already_indexed EXCEPTION;
    PRAGMA EXCEPTION_INIT(e_already_indexed, -1408);
BEGIN
    SELECT COUNT(*)
      INTO v_count
      FROM user_indexes
     WHERE table_name = 'PARTIES'
       AND index_name = 'IDX_PARTIES_GAMEDATE_STATUS_CREATED_ID';

    IF v_count = 0 THEN
        BEGIN
            EXECUTE IMMEDIATE
                'CREATE INDEX idx_parties_gamedate_status_created_id
                    ON parties(gamedate, status, createdat DESC, id DESC)';
        EXCEPTION
            WHEN e_index_exists OR e_already_indexed THEN
                NULL;
        END;
    END IF;
END;
/
