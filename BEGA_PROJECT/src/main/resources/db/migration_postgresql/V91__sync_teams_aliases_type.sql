-- V91: Align teams.aliases with LONGVARCHAR mapping.
-- PostgreSQL equivalent of Oracle V91.
-- In PostgreSQL, aliases should be TEXT. Converts VARCHAR → TEXT if needed.
-- V1 baseline already declares it as TEXT; this is a no-op on fresh envs.

DO $$
BEGIN
    IF to_regclass('public.teams') IS NULL THEN
        RETURN;
    END IF;

    IF EXISTS (
        SELECT 1
          FROM information_schema.columns
         WHERE table_schema = 'public'
           AND table_name = 'teams'
           AND column_name = 'aliases'
           AND data_type = 'character varying'
           AND character_maximum_length IS NOT NULL
           AND character_maximum_length <= 4000
    ) THEN
        EXECUTE 'ALTER TABLE teams ALTER COLUMN aliases TYPE TEXT';
    END IF;
END;
$$;
