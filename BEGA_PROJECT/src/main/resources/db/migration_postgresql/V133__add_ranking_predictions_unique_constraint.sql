-- V133: Enforce one ranking prediction per user and season (PostgreSQL)

DO $$
DECLARE
    v_duplicate_count BIGINT;
BEGIN
    IF to_regclass('public.ranking_predictions') IS NULL THEN
        RETURN;
    END IF;

    SELECT COUNT(*)
    INTO v_duplicate_count
    FROM (
        SELECT user_id, season_year
        FROM ranking_predictions
        WHERE user_id IS NOT NULL
        GROUP BY user_id, season_year
        HAVING COUNT(*) > 1
    ) existing_duplicates;

    IF v_duplicate_count > 0 THEN
        RAISE EXCEPTION 'Cannot apply V133: duplicate ranking_predictions rows already exist.';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint c
        JOIN pg_class r ON c.conrelid = r.oid
        WHERE r.relname = 'ranking_predictions'
          AND c.conname = 'uk_rank_pred_user_season'
    ) THEN
        ALTER TABLE ranking_predictions
            ADD CONSTRAINT uk_rank_pred_user_season UNIQUE (user_id, season_year);
    END IF;
END $$;
