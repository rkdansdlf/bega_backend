-- V67: Improve /matches/range and vote aggregation lookup indexes (PostgreSQL)

CREATE INDEX IF NOT EXISTS idx_game_range_filter
    ON game (game_date, is_dummy, game_status, home_team, away_team, game_id);

CREATE INDEX IF NOT EXISTS idx_game_status_dummy_date
    ON game (game_status, is_dummy, game_date);

CREATE INDEX IF NOT EXISTS idx_predictions_game_voted
    ON predictions (game_id, voted_team);
