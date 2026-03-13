-- V75: Ensure parties.gamedate exists and sync with legacy variants.
-- PostgreSQL equivalent of Oracle V75. Column likely already exists (referenced by V60 index).

ALTER TABLE IF EXISTS parties
    ADD COLUMN IF NOT EXISTS gamedate DATE;

DO $$
BEGIN
    IF to_regclass('public.parties') IS NULL THEN
        RETURN;
    END IF;

    -- Backfill from game_date if it exists
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'parties' AND column_name = 'game_date'
    ) THEN
        EXECUTE 'UPDATE parties SET gamedate = game_date WHERE gamedate IS NULL AND game_date IS NOT NULL';
    ELSIF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'parties' AND column_name = 'game_date_local'
    ) THEN
        EXECUTE 'UPDATE parties SET gamedate = game_date_local::DATE WHERE gamedate IS NULL AND game_date_local IS NOT NULL';
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'parties' AND column_name = 'createdat'
    ) THEN
        EXECUTE 'UPDATE parties SET gamedate = createdat::DATE WHERE gamedate IS NULL AND createdat IS NOT NULL';
    END IF;

    EXECUTE 'UPDATE parties SET gamedate = CURRENT_DATE WHERE gamedate IS NULL';
END;
$$;

ALTER TABLE IF EXISTS parties
    ALTER COLUMN gamedate SET DEFAULT CURRENT_DATE;

ALTER TABLE IF EXISTS parties
    ALTER COLUMN gamedate SET NOT NULL;
