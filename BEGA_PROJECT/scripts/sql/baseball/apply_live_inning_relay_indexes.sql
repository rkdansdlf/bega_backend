\set ON_ERROR_STOP on

DO $$
BEGIN
  IF to_regclass('game_inning_scores') IS NULL THEN
    RAISE EXCEPTION 'MANUAL_BASEBALL_DATA_REQUIRED: missing table game_inning_scores';
  END IF;
  IF to_regclass('game_play_by_play') IS NULL THEN
    RAISE EXCEPTION 'MANUAL_BASEBALL_DATA_REQUIRED: missing table game_play_by_play';
  END IF;
END
$$;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_game_inning_scores_live
  ON game_inning_scores(game_id, inning, team_side);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_game_play_by_play_live
  ON game_play_by_play(game_id, id);
