-- V178: Add season settlement columns to ranking_predictions (PostgreSQL)

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'ranking_predictions'
          AND column_name = 'exact_match_count'
    ) THEN
        ALTER TABLE ranking_predictions ADD COLUMN exact_match_count SMALLINT;
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'ranking_predictions'
          AND column_name = 'settled_at'
    ) THEN
        ALTER TABLE ranking_predictions ADD COLUMN settled_at TIMESTAMP;
    END IF;
END;
$$;

CREATE INDEX IF NOT EXISTS idx_rank_pred_season_settled
    ON ranking_predictions (season_year, settled_at);
