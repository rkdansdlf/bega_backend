\set ON_ERROR_STOP on

DO $$
DECLARE
  inning_index_count integer;
  relay_index_count integer;
BEGIN
  SELECT count(*)
    INTO inning_index_count
    FROM pg_index index_state
    JOIN pg_class index_relation ON index_relation.oid = index_state.indexrelid
    JOIN pg_class table_relation ON table_relation.oid = index_state.indrelid
    JOIN pg_namespace table_namespace ON table_namespace.oid = table_relation.relnamespace
   WHERE table_namespace.nspname = current_schema()
     AND table_relation.relname = 'game_inning_scores'
     AND index_relation.relname = 'idx_game_inning_scores_live'
     AND index_state.indisvalid
     AND index_state.indisready
     AND lower(pg_get_indexdef(index_state.indexrelid)) LIKE '%(game_id, inning, team_side)%';

  SELECT count(*)
    INTO relay_index_count
    FROM pg_index index_state
    JOIN pg_class index_relation ON index_relation.oid = index_state.indexrelid
    JOIN pg_class table_relation ON table_relation.oid = index_state.indrelid
    JOIN pg_namespace table_namespace ON table_namespace.oid = table_relation.relnamespace
   WHERE table_namespace.nspname = current_schema()
     AND table_relation.relname = 'game_play_by_play'
     AND index_relation.relname = 'idx_game_play_by_play_live'
     AND index_state.indisvalid
     AND index_state.indisready
     AND lower(pg_get_indexdef(index_state.indexrelid)) LIKE '%(game_id, id)%';

  IF inning_index_count <> 1 THEN
    RAISE EXCEPTION 'missing or drifted index idx_game_inning_scores_live';
  END IF;
  IF relay_index_count <> 1 THEN
    RAISE EXCEPTION 'missing or drifted index idx_game_play_by_play_live';
  END IF;
END
$$;
