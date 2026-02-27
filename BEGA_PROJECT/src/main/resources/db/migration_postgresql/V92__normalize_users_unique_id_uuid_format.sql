-- V92: Normalize users.unique_id values to canonical UUID format.
-- PostgreSQL equivalent of Oracle V92.
-- In PostgreSQL, unique_id is UUID type (V52 bootstrap). The column stores values
-- in canonical 8-4-4-4-12 format natively, so this is effectively a no-op.
-- For environments where unique_id was stored as VARCHAR before type migration,
-- this UPDATE normalizes any 32-hex-char values that may have slipped through.

DO $$
BEGIN
    IF to_regclass('public.users') IS NULL THEN
        RETURN;
    END IF;

    -- Only run if unique_id is a text-like column (VARCHAR/TEXT), not UUID type.
    -- On UUID columns, values are already canonical; attempting regex UPDATE would fail type cast.
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'users'
          AND column_name = 'unique_id'
          AND data_type IN ('character varying', 'text', 'character')
    ) THEN
        EXECUTE $q$
            UPDATE users
               SET unique_id = LOWER(
                   REGEXP_REPLACE(
                       unique_id::text,
                       '^([0-9a-fA-F]{8})([0-9a-fA-F]{4})([0-9a-fA-F]{4})([0-9a-fA-F]{4})([0-9a-fA-F]{12})$',
                       '\1-\2-\3-\4-\5'
                   )
               )
             WHERE unique_id IS NOT NULL
               AND unique_id::text ~ '^[0-9a-fA-F]{32}$'
        $q$;
    END IF;
END;
$$;
