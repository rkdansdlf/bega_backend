-- V76: Ensure parties.gametime exists and sync with legacy variants.
-- Some environments were created with game_time or game_time_local.

DECLARE
    v_count NUMBER;
    v_datatype VARCHAR2(128);
BEGIN
    SELECT COUNT(*)
      INTO v_count
      FROM user_tab_cols
     WHERE table_name = 'PARTIES'
       AND column_name = 'GAMETIME';

    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE parties ADD (gametime TIMESTAMP(6))';

        SELECT COUNT(*)
          INTO v_count
          FROM user_tab_cols
         WHERE table_name = 'PARTIES'
           AND column_name = 'GAME_TIME';

        IF v_count > 0 THEN
            SELECT data_type
              INTO v_datatype
              FROM user_tab_cols
             WHERE table_name = 'PARTIES'
               AND column_name = 'GAME_TIME';

            IF v_datatype = 'TIMESTAMP(6)' OR v_datatype = 'TIMESTAMP(9)' THEN
                EXECUTE IMMEDIATE 'UPDATE parties SET gametime = game_time WHERE gametime IS NULL AND game_time IS NOT NULL';
            ELSIF v_datatype = 'DATE' OR v_datatype LIKE 'TIMESTAMP%' THEN
                EXECUTE IMMEDIATE 'UPDATE parties SET gametime = CAST(game_time AS TIMESTAMP) WHERE gametime IS NULL AND game_time IS NOT NULL';
            ELSE
                BEGIN
                    EXECUTE IMMEDIATE 'UPDATE parties SET gametime = TO_TIMESTAMP(game_time, ''HH24:MI:SS'') WHERE gametime IS NULL AND game_time IS NOT NULL';
                EXCEPTION
                    WHEN OTHERS THEN
                        EXECUTE IMMEDIATE 'UPDATE parties SET gametime = TO_TIMESTAMP(game_time, ''HH24:MI'') WHERE gametime IS NULL AND game_time IS NOT NULL';
                END;
            END IF;
        ELSE
            SELECT COUNT(*)
              INTO v_count
              FROM user_tab_cols
             WHERE table_name = 'PARTIES'
               AND column_name = 'GAME_TIME_LOCAL';

            IF v_count > 0 THEN
                BEGIN
                    EXECUTE IMMEDIATE 'UPDATE parties SET gametime = TO_TIMESTAMP(REGEXP_SUBSTR(game_time_local, ''[0-2]?[0-9]:[0-5][0-9](:[0-5][0-9])?''), ''HH24:MI:SS'') WHERE gametime IS NULL AND game_time_local IS NOT NULL';
                EXCEPTION
                    WHEN OTHERS THEN
                        EXECUTE IMMEDIATE 'UPDATE parties SET gametime = TO_TIMESTAMP(REGEXP_SUBSTR(game_time_local, ''[0-2]?[0-9]:[0-5][0-9]''), ''HH24:MI'') WHERE gametime IS NULL AND game_time_local IS NOT NULL';
                END;
            END IF;
        END IF;

        EXECUTE IMMEDIATE 'UPDATE parties SET gametime = TO_TIMESTAMP(''00:00:00'', ''HH24:MI:SS'') WHERE gametime IS NULL';
        EXECUTE IMMEDIATE 'ALTER TABLE parties MODIFY (gametime TIMESTAMP(6) NOT NULL)';
        EXECUTE IMMEDIATE 'ALTER TABLE parties MODIFY (gametime DEFAULT TO_TIMESTAMP(''00:00:00'', ''HH24:MI:SS''))';
    END IF;
END;
/
