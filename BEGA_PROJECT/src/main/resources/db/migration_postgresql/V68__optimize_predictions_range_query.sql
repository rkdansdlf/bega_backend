-- V68: Add focused indexes for prediction match-range and vote lookup paths

CREATE INDEX IF NOT EXISTS idx_game_range_canonical_active
    ON game (game_date, home_team, away_team, game_id)
    WHERE is_dummy IS NOT TRUE
      AND game_id NOT LIKE 'MOCK%';

CREATE INDEX IF NOT EXISTS idx_predictions_game
    ON predictions (game_id);
