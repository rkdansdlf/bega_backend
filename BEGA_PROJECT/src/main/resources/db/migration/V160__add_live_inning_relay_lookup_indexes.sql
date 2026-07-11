-- V160: Speed up live score and relay polling lookups on Oracle.

DECLARE
    v_table_count NUMBER;
    v_index_count NUMBER;
BEGIN
    SELECT COUNT(*)
      INTO v_table_count
      FROM user_tables
     WHERE table_name = 'GAME_INNING_SCORES';

    IF v_table_count > 0 THEN
        SELECT COUNT(*)
          INTO v_index_count
          FROM user_indexes
         WHERE table_name = 'GAME_INNING_SCORES'
           AND index_name = 'IDX_GAME_INNING_SCORES_LIVE';

        IF v_index_count = 0 THEN
            EXECUTE IMMEDIATE 'CREATE INDEX IDX_GAME_INNING_SCORES_LIVE ON game_inning_scores(game_id, inning, team_side)';
        END IF;
    END IF;

    SELECT COUNT(*)
      INTO v_table_count
      FROM user_tables
     WHERE table_name = 'GAME_PLAY_BY_PLAY';

    IF v_table_count > 0 THEN
        SELECT COUNT(*)
          INTO v_index_count
          FROM user_indexes
         WHERE table_name = 'GAME_PLAY_BY_PLAY'
           AND index_name = 'IDX_GAME_PLAY_BY_PLAY_LIVE';

        IF v_index_count = 0 THEN
            EXECUTE IMMEDIATE 'CREATE INDEX IDX_GAME_PLAY_BY_PLAY_LIVE ON game_play_by_play(game_id, id)';
        END IF;
    END IF;
END;
/
