-- V172: Add season settlement columns to ranking_predictions (Oracle)

DECLARE
    v_table_count NUMBER;
    v_column_count NUMBER;
BEGIN
    SELECT COUNT(*)
    INTO v_table_count
    FROM user_tables
    WHERE table_name = 'RANKING_PREDICTIONS';

    IF v_table_count = 0 THEN
        RETURN;
    END IF;

    SELECT COUNT(*)
    INTO v_column_count
    FROM user_tab_cols
    WHERE table_name = 'RANKING_PREDICTIONS'
      AND column_name = 'EXACT_MATCH_COUNT';

    IF v_column_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE ranking_predictions ADD exact_match_count NUMBER(2)';
    END IF;

    SELECT COUNT(*)
    INTO v_column_count
    FROM user_tab_cols
    WHERE table_name = 'RANKING_PREDICTIONS'
      AND column_name = 'SETTLED_AT';

    IF v_column_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE ranking_predictions ADD settled_at TIMESTAMP';
    END IF;
END;
/

DECLARE
    v_index_count NUMBER;
BEGIN
    SELECT COUNT(*)
    INTO v_index_count
    FROM user_indexes
    WHERE index_name = 'IDX_RANK_PRED_SEASON_SETTLED';

    IF v_index_count = 0 THEN
        EXECUTE IMMEDIATE
            'CREATE INDEX idx_rank_pred_season_settled ON ranking_predictions (season_year, settled_at)';
    END IF;
END;
/
