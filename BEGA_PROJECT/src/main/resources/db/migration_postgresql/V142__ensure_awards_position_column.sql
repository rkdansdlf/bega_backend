-- Ensure legacy awards tables have the position column expected by AwardEntity.

DO $$
BEGIN
    IF to_regclass('public.awards') IS NOT NULL THEN
        IF EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = 'public'
              AND table_name = 'awards'
              AND column_name = 'player_position'
        ) AND NOT EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = 'public'
              AND table_name = 'awards'
              AND column_name = 'position'
        ) THEN
            ALTER TABLE public.awards RENAME COLUMN player_position TO position;
        ELSE
            ALTER TABLE public.awards ADD COLUMN IF NOT EXISTS position VARCHAR(50);
        END IF;
    END IF;
END;
$$;
