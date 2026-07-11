-- V165: Add created-at pagination indexes for public mate party lists (Oracle).

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
       AND index_name = 'IDX_PARTIES_CREATED_ID';

    IF v_count = 0 THEN
        BEGIN
            EXECUTE IMMEDIATE
                'CREATE INDEX idx_parties_created_id
                    ON parties(createdat DESC, id DESC)';
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
       AND index_name = 'IDX_PARTIES_TEAM_CREATED_ID';

    IF v_count = 0 THEN
        BEGIN
            EXECUTE IMMEDIATE
                'CREATE INDEX idx_parties_team_created_id
                    ON parties(teamid, createdat DESC, id DESC)';
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
       AND index_name = 'IDX_PARTIES_GAMEDATE_CREATED_ID';

    IF v_count = 0 THEN
        BEGIN
            EXECUTE IMMEDIATE
                'CREATE INDEX idx_parties_gamedate_created_id
                    ON parties(gamedate, createdat DESC, id DESC)';
        EXCEPTION
            WHEN e_index_exists OR e_already_indexed THEN
                NULL;
        END;
    END IF;
END;
/
