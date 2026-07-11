-- V166: Add participant-count sort indexes for public mate party lists (Oracle).

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
       AND index_name = 'IDX_PARTIES_CURRENT_ID';

    IF v_count = 0 THEN
        BEGIN
            EXECUTE IMMEDIATE
                'CREATE INDEX idx_parties_current_id
                    ON parties(currentparticipants DESC, id DESC)';
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
       AND index_name = 'IDX_PARTIES_TEAM_CURRENT_ID';

    IF v_count = 0 THEN
        BEGIN
            EXECUTE IMMEDIATE
                'CREATE INDEX idx_parties_team_current_id
                    ON parties(teamid, currentparticipants DESC, id DESC)';
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
       AND index_name = 'IDX_PARTIES_DATE_CURRENT_ID';

    IF v_count = 0 THEN
        BEGIN
            EXECUTE IMMEDIATE
                'CREATE INDEX idx_parties_date_current_id
                    ON parties(gamedate, currentparticipants DESC, id DESC)';
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
       AND index_name = 'IDX_PARTIES_STATUS_CURRENT_ID';

    IF v_count = 0 THEN
        BEGIN
            EXECUTE IMMEDIATE
                'CREATE INDEX idx_parties_status_current_id
                    ON parties(status, currentparticipants DESC, id DESC)';
        EXCEPTION
            WHEN e_index_exists OR e_already_indexed THEN
                NULL;
        END;
    END IF;
END;
/
