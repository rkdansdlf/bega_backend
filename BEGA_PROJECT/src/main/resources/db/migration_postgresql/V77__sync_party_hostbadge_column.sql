-- V77: Ensure parties.hostbadge exists and sync with legacy variants.
-- PostgreSQL equivalent of Oracle V77.

ALTER TABLE IF EXISTS parties
    ADD COLUMN IF NOT EXISTS hostbadge VARCHAR(20);

DO $$
BEGIN
    IF to_regclass('public.parties') IS NULL THEN
        RETURN;
    END IF;

    -- Backfill from host_badge if it exists
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'parties' AND column_name = 'host_badge'
    ) THEN
        EXECUTE 'UPDATE parties SET hostbadge = host_badge WHERE hostbadge IS NULL AND host_badge IS NOT NULL';
    END IF;

    EXECUTE 'UPDATE parties SET hostbadge = ''NEW'' WHERE hostbadge IS NULL';
END;
$$;

ALTER TABLE IF EXISTS parties
    ALTER COLUMN hostbadge SET DEFAULT 'NEW';

ALTER TABLE IF EXISTS parties
    ALTER COLUMN hostbadge SET NOT NULL;
