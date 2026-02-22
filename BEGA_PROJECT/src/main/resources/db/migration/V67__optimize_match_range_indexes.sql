-- V67: Improve /matches/range and vote aggregation lookup indexes (Oracle)

DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*)
      INTO v_count
      FROM user_indexes
     WHERE table_name = 'GAME'
       AND index_name = 'IDX_GAME_RANGE_FILTER';

    IF v_count = 0 THEN
        EXECUTE IMMEDIATE
            'CREATE INDEX IDX_GAME_RANGE_FILTER ON game(game_date, is_dummy, game_status, home_team, away_team, game_id)';
    END IF;
END;
/

DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*)
      INTO v_count
      FROM user_indexes
     WHERE table_name = 'GAME'
       AND index_name = 'IDX_GAME_STATUS_DUMMY_DATE';

    IF v_count = 0 THEN
        EXECUTE IMMEDIATE
            'CREATE INDEX IDX_GAME_STATUS_DUMMY_DATE ON game(game_status, is_dummy, game_date)';
    END IF;
END;
/

DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*)
      INTO v_count
      FROM user_indexes
     WHERE table_name = 'PREDICTIONS'
       AND index_name = 'IDX_PREDICTIONS_GAME_VOTED';

    IF v_count = 0 THEN
        EXECUTE IMMEDIATE
            'CREATE INDEX IDX_PREDICTIONS_GAME_VOTED ON predictions(game_id, voted_team)';
    END IF;
END;
/
