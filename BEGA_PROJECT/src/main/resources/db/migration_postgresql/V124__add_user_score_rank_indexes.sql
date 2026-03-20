-- V124: Add indexes on user_score score columns to speed up COUNT(*)+1 rank queries
-- Each of the 4 rank queries does: SELECT COUNT(us)+1 FROM user_score WHERE x_score > :score
-- These indexes enable index-only scans instead of full table scans.

DO $$
BEGIN
    IF to_regclass('public.user_score') IS NULL THEN
        RETURN;
    END IF;

    CREATE INDEX IF NOT EXISTS idx_user_score_total_score
        ON user_score (total_score DESC);

    CREATE INDEX IF NOT EXISTS idx_user_score_season_score
        ON user_score (season_score DESC);

    CREATE INDEX IF NOT EXISTS idx_user_score_monthly_score
        ON user_score (monthly_score DESC);

    CREATE INDEX IF NOT EXISTS idx_user_score_weekly_score
        ON user_score (weekly_score DESC);
END $$;
