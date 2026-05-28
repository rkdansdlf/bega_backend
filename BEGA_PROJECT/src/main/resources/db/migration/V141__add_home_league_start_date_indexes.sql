-- V141: Support home bootstrap league start-date lookups on Oracle.

DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*)
      INTO v_count
      FROM user_indexes
     WHERE table_name = 'GAME'
       AND index_name = 'IDX_HOME_GAME_SEASON_DATE';

    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE INDEX IDX_HOME_GAME_SEASON_DATE ON game(season_id, game_date)';
    END IF;
END;
/

DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*)
      INTO v_count
      FROM user_indexes
     WHERE table_name = 'KBO_SEASONS'
       AND index_name = 'IDX_HOME_SEASONS_YEAR_TYPE';

    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE INDEX IDX_HOME_SEASONS_YEAR_TYPE ON kbo_seasons(season_year, league_type_code, start_date, season_id)';
    END IF;
END;
/

DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*)
      INTO v_count
      FROM user_indexes
     WHERE table_name = 'KBO_SEASONS'
       AND index_name = 'IDX_HOME_SEASONS_TYPE_ASOF';

    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE INDEX IDX_HOME_SEASONS_TYPE_ASOF ON kbo_seasons(league_type_code, start_date, season_year)';
    END IF;
END;
/
