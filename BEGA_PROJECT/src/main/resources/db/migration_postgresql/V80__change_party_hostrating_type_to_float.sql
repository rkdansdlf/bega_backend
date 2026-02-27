-- V80: Align parties.hostrating column type with JPA Double mapping.
-- PostgreSQL equivalent of Oracle V80. Converts NUMERIC(3,2) → DOUBLE PRECISION.

DO $$
BEGIN
    IF to_regclass('public.parties') IS NULL THEN
        RETURN;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'parties'
          AND column_name = 'hostrating'
    ) THEN
        RETURN;
    END IF;

    -- Change to DOUBLE PRECISION only if not already that type
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'parties'
          AND column_name = 'hostrating'
          AND data_type <> 'double precision'
    ) THEN
        EXECUTE 'ALTER TABLE parties ALTER COLUMN hostrating TYPE DOUBLE PRECISION USING hostrating::DOUBLE PRECISION';
        EXECUTE 'ALTER TABLE parties ALTER COLUMN hostrating SET DEFAULT 5.0';
    END IF;
END;
$$;
