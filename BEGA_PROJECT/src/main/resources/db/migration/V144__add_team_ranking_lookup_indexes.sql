-- V144: Speed up home ranking snapshot cold season aggregation on Oracle.

DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*)
      INTO v_count
      FROM user_indexes
     WHERE table_name = 'GAME'
       AND index_name = 'IDX_GAME_RANK_SEASON_COMPLETED';

    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE INDEX IDX_GAME_RANK_SEASON_COMPLETED ON game(season_id, game_date, home_team, away_team, game_id)';
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
       AND index_name = 'IDX_GAME_RANK_DATE_COMPLETED';

    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE INDEX IDX_GAME_RANK_DATE_COMPLETED ON game(game_date, season_id, home_team, away_team, game_id)';
    END IF;
END;
/
