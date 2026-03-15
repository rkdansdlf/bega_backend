-- V67: Improve /matches/range and vote aggregation lookup indexes (PostgreSQL)

DO $$
BEGIN
    IF to_regclass('public.game') IS NULL THEN
        RETURN;
    END IF;

    CREATE INDEX IF NOT EXISTS idx_game_range_filter
        ON game (game_date, is_dummy, game_status, home_team, away_team, game_id);

    CREATE INDEX IF NOT EXISTS idx_game_status_dummy_date
        ON game (game_status, is_dummy, game_date);
END $$;

DO $$
BEGIN
    IF to_regclass('public.predictions') IS NULL THEN
        RETURN;
    END IF;

    CREATE INDEX IF NOT EXISTS idx_predictions_game_voted
        ON predictions (game_id, voted_team);
END $$;
