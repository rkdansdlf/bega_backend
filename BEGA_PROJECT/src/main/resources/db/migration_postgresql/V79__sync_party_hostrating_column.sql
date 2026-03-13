-- V79: Ensure parties.hostrating exists with valid defaults.
-- PostgreSQL equivalent of Oracle V79. Column will be NUMERIC(3,2) initially;
-- V80 will change it to DOUBLE PRECISION.

ALTER TABLE IF EXISTS parties
    ADD COLUMN IF NOT EXISTS hostrating NUMERIC(3,2);

DO $$
BEGIN
    IF to_regclass('public.parties') IS NULL THEN
        RETURN;
    END IF;

    -- Backfill from host_rating if it exists
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'parties' AND column_name = 'host_rating'
    ) THEN
        EXECUTE 'UPDATE parties SET hostrating = host_rating WHERE hostrating IS NULL AND host_rating IS NOT NULL';
    END IF;

    EXECUTE 'UPDATE parties SET hostrating = 5.0 WHERE hostrating IS NULL';
END;
$$;

ALTER TABLE IF EXISTS parties
    ALTER COLUMN hostrating SET DEFAULT 5.0;

ALTER TABLE IF EXISTS parties
    ALTER COLUMN hostrating SET NOT NULL;
