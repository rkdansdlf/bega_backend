-- V148: Speed up home ranking snapshot cold season aggregation on PostgreSQL.

CREATE INDEX IF NOT EXISTS idx_game_rank_season_completed
  ON game (season_id, game_date, home_team, away_team, game_id)
  WHERE home_score IS NOT NULL
    AND away_score IS NOT NULL
    AND is_dummy IS NOT TRUE
    AND game_id NOT LIKE 'MOCK%';

CREATE INDEX IF NOT EXISTS idx_game_rank_date_completed
  ON game (game_date, home_team, away_team, game_id)
  WHERE season_id IS NULL
    AND home_score IS NOT NULL
    AND away_score IS NOT NULL
    AND is_dummy IS NOT TRUE
    AND game_id NOT LIKE 'MOCK%';
