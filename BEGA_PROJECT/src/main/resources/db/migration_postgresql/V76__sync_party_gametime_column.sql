-- V76: Ensure parties.gametime exists and sync with legacy variants.
-- PostgreSQL equivalent of Oracle V76.

ALTER TABLE IF EXISTS parties
    ADD COLUMN IF NOT EXISTS gametime TIMESTAMP;

DO $$
BEGIN
    IF to_regclass('public.parties') IS NULL THEN
        RETURN;
    END IF;

    -- Backfill from game_time if it exists
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'parties' AND column_name = 'game_time'
    ) THEN
        EXECUTE 'UPDATE parties SET gametime = game_time::TIMESTAMP WHERE gametime IS NULL AND game_time IS NOT NULL';
    ELSIF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'parties' AND column_name = 'game_time_local'
    ) THEN
        EXECUTE $q$
            UPDATE parties
               SET gametime = TO_TIMESTAMP(
                       SUBSTRING(game_time_local FROM '[0-2]?[0-9]:[0-5][0-9](:[0-5][0-9])?'),
                       'HH24:MI:SS'
                   )
             WHERE gametime IS NULL AND game_time_local IS NOT NULL
        $q$;
    END IF;

    EXECUTE 'UPDATE parties SET gametime = ''1970-01-01 00:00:00'' WHERE gametime IS NULL';
END;
$$;

ALTER TABLE IF EXISTS parties
    ALTER COLUMN gametime SET DEFAULT '1970-01-01 00:00:00';

ALTER TABLE IF EXISTS parties
    ALTER COLUMN gametime SET NOT NULL;
