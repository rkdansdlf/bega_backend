-- V78: Ensure parties.hostid and parties.hostname exist.
-- PostgreSQL equivalent of Oracle V78.

ALTER TABLE IF EXISTS parties
    ADD COLUMN IF NOT EXISTS hostid BIGINT;

ALTER TABLE IF EXISTS parties
    ADD COLUMN IF NOT EXISTS hostname VARCHAR(50);

DO $$
BEGIN
    IF to_regclass('public.parties') IS NULL THEN
        RETURN;
    END IF;

    -- Backfill hostid from host_id
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'parties' AND column_name = 'host_id'
    ) THEN
        EXECUTE 'UPDATE parties SET hostid = host_id WHERE hostid IS NULL AND host_id IS NOT NULL';
    END IF;
    EXECUTE 'UPDATE parties SET hostid = -1 WHERE hostid IS NULL';

    -- Backfill hostname from host_name
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'parties' AND column_name = 'host_name'
    ) THEN
        EXECUTE 'UPDATE parties SET hostname = host_name WHERE hostname IS NULL AND host_name IS NOT NULL';
    END IF;
    EXECUTE 'UPDATE parties SET hostname = ''UNKNOWN'' WHERE hostname IS NULL';
END;
$$;

ALTER TABLE IF EXISTS parties
    ALTER COLUMN hostid SET DEFAULT -1;
ALTER TABLE IF EXISTS parties
    ALTER COLUMN hostid SET NOT NULL;

ALTER TABLE IF EXISTS parties
    ALTER COLUMN hostname SET DEFAULT 'UNKNOWN';
ALTER TABLE IF EXISTS parties
    ALTER COLUMN hostname SET NOT NULL;
