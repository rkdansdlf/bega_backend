-- V68: Add lookup index for /api/matches/range canonical query path (Oracle)

DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*)
      INTO v_count
      FROM user_indexes
     WHERE table_name = 'GAME'
       AND index_name = 'IDX_GAME_RANGE_LOOKUP';

    IF v_count = 0 THEN
        EXECUTE IMMEDIATE
            'CREATE INDEX IDX_GAME_RANGE_LOOKUP ON game(game_date, is_dummy, home_team, away_team, game_id)';
    END IF;
END;
/
