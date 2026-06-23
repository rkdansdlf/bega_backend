-- V152: Repair missing canonical /matches/range lookup index (Oracle).

DECLARE
    v_count NUMBER;
    e_columns_already_indexed EXCEPTION;
    PRAGMA EXCEPTION_INIT(e_columns_already_indexed, -1408);
BEGIN
    SELECT COUNT(*)
      INTO v_count
      FROM user_tables
     WHERE table_name = 'GAME';

    IF v_count > 0 THEN
        SELECT COUNT(*)
          INTO v_count
          FROM user_ind_columns
         WHERE table_name = 'GAME'
           AND index_name = 'IDX_GAME_RANGE_CANONICAL_ACTIVE'
           AND (
                (column_name = 'GAME_DATE' AND column_position = 1)
             OR (column_name = 'IS_DUMMY' AND column_position = 2)
             OR (column_name = 'HOME_TEAM' AND column_position = 3)
             OR (column_name = 'AWAY_TEAM' AND column_position = 4)
             OR (column_name = 'GAME_ID' AND column_position = 5)
           );

        IF v_count < 5 THEN
            BEGIN
                EXECUTE IMMEDIATE
                    'CREATE INDEX idx_game_range_canonical_active
                        ON game(game_date, is_dummy, home_team, away_team, game_id)';
            EXCEPTION
                WHEN e_columns_already_indexed THEN NULL;
            END;
        END IF;
    END IF;
END;
/
