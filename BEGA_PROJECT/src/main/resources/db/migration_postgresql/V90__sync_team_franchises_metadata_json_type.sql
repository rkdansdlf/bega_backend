-- V90: Align team_franchises.metadata_json with LONGVARCHAR mapping.
-- PostgreSQL equivalent of Oracle V90.
-- In PostgreSQL, metadata_json should be TEXT. Converts VARCHAR → TEXT if needed.
-- V1 baseline already declares it as TEXT; this is a no-op on fresh envs.

DO $$
BEGIN
    IF to_regclass('public.team_franchises') IS NULL THEN
        RETURN;
    END IF;

    IF EXISTS (
        SELECT 1
          FROM information_schema.columns
         WHERE table_schema = 'public'
           AND table_name = 'team_franchises'
           AND column_name = 'metadata_json'
           AND data_type = 'character varying'
           AND character_maximum_length IS NOT NULL
           AND character_maximum_length <= 4000
    ) THEN
        EXECUTE 'ALTER TABLE team_franchises ALTER COLUMN metadata_json TYPE TEXT';
    END IF;
END;
$$;
