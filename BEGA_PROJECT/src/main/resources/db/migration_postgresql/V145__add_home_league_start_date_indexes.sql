-- V145: Support home bootstrap league start-date lookups on PostgreSQL.

CREATE INDEX IF NOT EXISTS idx_home_game_season_date
  ON game(season_id, game_date);

CREATE INDEX IF NOT EXISTS idx_home_seasons_year_type
  ON kbo_seasons(season_year, league_type_code, start_date, season_id);

CREATE INDEX IF NOT EXISTS idx_home_seasons_type_asof
  ON kbo_seasons(league_type_code, start_date, season_year);
