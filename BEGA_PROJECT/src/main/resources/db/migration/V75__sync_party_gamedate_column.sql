-- V75: Ensure parties.gamedate exists and sync with legacy variants.
-- Some environments were created with game_date or game_date_local.

DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*)
      INTO v_count
     FROM user_tab_cols
     WHERE table_name = 'PARTIES'
       AND column_name = 'GAMEDATE';

    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE parties ADD (gamedate DATE)';

        SELECT COUNT(*)
          INTO v_count
          FROM user_tab_cols
         WHERE table_name = 'PARTIES'
           AND column_name = 'GAME_DATE';

        IF v_count > 0 THEN
            EXECUTE IMMEDIATE 'UPDATE parties SET gamedate = game_date WHERE gamedate IS NULL AND game_date IS NOT NULL';
        ELSE
            SELECT COUNT(*)
              INTO v_count
              FROM user_tab_cols
             WHERE table_name = 'PARTIES'
               AND column_name = 'GAME_DATE_LOCAL';

            IF v_count > 0 THEN
                BEGIN
                    EXECUTE IMMEDIATE 'UPDATE parties SET gamedate = TO_DATE(game_date_local, ''YYYY-MM-DD'') WHERE gamedate IS NULL AND game_date_local IS NOT NULL';
                EXCEPTION
                    WHEN OTHERS THEN
                        EXECUTE IMMEDIATE 'UPDATE parties SET gamedate = TO_DATE(REGEXP_SUBSTR(game_date_local, ''[0-9]{4}-[0-9]{2}-[0-9]{2}''), ''YYYY-MM-DD'') WHERE gamedate IS NULL AND game_date_local IS NOT NULL';
                END;
            END IF;
        END IF;

        EXECUTE IMMEDIATE 'UPDATE parties SET gamedate = TRUNC(createdat) WHERE gamedate IS NULL AND createdat IS NOT NULL';
        EXECUTE IMMEDIATE 'UPDATE parties SET gamedate = TRUNC(SYSDATE) WHERE gamedate IS NULL';
        EXECUTE IMMEDIATE 'ALTER TABLE parties MODIFY (gamedate DATE NOT NULL)';
        EXECUTE IMMEDIATE 'ALTER TABLE parties MODIFY (gamedate DEFAULT SYSDATE)';
    END IF;
END;
/
