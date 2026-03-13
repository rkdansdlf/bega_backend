-- V74: Align score_events.multiplier with shared NUMERIC mapping.

DO $$
BEGIN
    IF to_regclass('public.score_events') IS NULL THEN
        RETURN;
    END IF;

    IF NOT EXISTS (
        SELECT 1
          FROM information_schema.columns
         WHERE table_schema = 'public'
           AND table_name = 'score_events'
           AND column_name = 'multiplier'
    ) THEN
        RETURN;
    END IF;

    EXECUTE '
        ALTER TABLE public.score_events
        ALTER COLUMN multiplier TYPE NUMERIC(5,2)
        USING ROUND(multiplier::numeric, 2)
    ';

    EXECUTE '
        UPDATE public.score_events
           SET multiplier = 1.00
         WHERE multiplier IS NULL
    ';
END $$;

ALTER TABLE IF EXISTS public.score_events
    ALTER COLUMN multiplier SET DEFAULT 1.00;

ALTER TABLE IF EXISTS public.score_events
    ALTER COLUMN multiplier SET NOT NULL;
