-- V6: Speed up live score and relay polling lookups on the baseball PostgreSQL schema.

DO $$
BEGIN
  IF to_regclass('game_inning_scores') IS NOT NULL THEN
    CREATE INDEX IF NOT EXISTS idx_game_inning_scores_live
      ON game_inning_scores(game_id, inning, team_side);
  END IF;
END
$$;

DO $$
BEGIN
  IF to_regclass('game_play_by_play') IS NOT NULL THEN
    CREATE INDEX IF NOT EXISTS idx_game_play_by_play_live
      ON game_play_by_play(game_id, id);
  END IF;
END
$$;
