-- V166: Speed up live score and relay polling lookups on PostgreSQL.

CREATE INDEX IF NOT EXISTS idx_game_inning_scores_live
  ON game_inning_scores(game_id, inning, team_side);

CREATE INDEX IF NOT EXISTS idx_game_play_by_play_live
  ON game_play_by_play(game_id, id);
