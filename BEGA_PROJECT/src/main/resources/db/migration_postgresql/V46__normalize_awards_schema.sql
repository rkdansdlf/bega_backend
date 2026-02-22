-- Normalize public.awards schema to match JPA mapping used in dev profile.
-- This migration is idempotent and can be re-run safely.

DO $$
BEGIN
  IF to_regclass('public.awards') IS NOT NULL THEN
    IF EXISTS (
      SELECT 1
      FROM information_schema.columns
      WHERE table_schema = 'public'
        AND table_name = 'awards'
        AND column_name = 'id'
        AND data_type = 'integer'
    ) THEN
      ALTER TABLE public.awards
      ALTER COLUMN id TYPE BIGINT;
    END IF;

    IF EXISTS (
      SELECT 1
      FROM information_schema.columns
      WHERE table_schema = 'public'
        AND table_name = 'awards'
        AND column_name = 'year'
        AND NOT EXISTS (
          SELECT 1
          FROM information_schema.columns
          WHERE table_schema = 'public'
            AND table_name = 'awards'
            AND column_name = 'award_year'
        )
    ) THEN
      ALTER TABLE public.awards RENAME COLUMN year TO award_year;
    END IF;

    IF NOT EXISTS (
      SELECT 1
      FROM information_schema.columns
      WHERE table_schema = 'public'
        AND table_name = 'awards'
        AND column_name = 'award_year'
    ) THEN
      ALTER TABLE public.awards
      ADD COLUMN award_year INTEGER;
    END IF;

    UPDATE public.awards
    SET award_year = COALESCE(award_year, EXTRACT(YEAR FROM CURRENT_DATE)::int)
    WHERE award_year IS NULL;

    ALTER TABLE public.awards
    ALTER COLUMN award_year SET NOT NULL;

    IF NOT EXISTS (
      SELECT 1
      FROM information_schema.columns
      WHERE table_schema = 'public'
        AND table_name = 'awards'
        AND column_name = 'position'
    ) THEN
      IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'awards'
          AND column_name = 'player_position'
      ) THEN
        ALTER TABLE public.awards RENAME COLUMN player_position TO position;
      ELSE
        ALTER TABLE public.awards
        ADD COLUMN IF NOT EXISTS position VARCHAR(50);
      END IF;
    END IF;
  END IF;
END;
$$;
