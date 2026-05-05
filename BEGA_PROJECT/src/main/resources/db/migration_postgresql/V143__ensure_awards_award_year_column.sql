-- Ensure legacy awards tables expose award_year without synthesizing baseball data.

DO $$
DECLARE
    awards_row_count bigint;
BEGIN
    IF to_regclass('public.awards') IS NOT NULL THEN
        IF NOT EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = 'public'
              AND table_name = 'awards'
              AND column_name = 'award_year'
        ) THEN
            IF EXISTS (
                SELECT 1
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = 'awards'
                  AND column_name = 'year'
            ) THEN
                ALTER TABLE public.awards RENAME COLUMN year TO award_year;
            ELSE
                SELECT COUNT(*) INTO awards_row_count FROM public.awards;

                IF awards_row_count > 0 THEN
                    RAISE EXCEPTION
                        'MANUAL_BASEBALL_DATA_REQUIRED: public.awards.award_year is missing and cannot be derived from an existing year column. Provide operator-verified award_year data before starting the backend.';
                END IF;

                ALTER TABLE public.awards ADD COLUMN award_year INTEGER;
            END IF;
        END IF;

        IF EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = 'public'
              AND table_name = 'awards'
              AND column_name = 'year'
        ) THEN
            UPDATE public.awards
            SET award_year = year
            WHERE award_year IS NULL
              AND year IS NOT NULL;
        END IF;

        IF EXISTS (
            SELECT 1
            FROM public.awards
            WHERE award_year IS NULL
        ) THEN
            RAISE EXCEPTION
                'MANUAL_BASEBALL_DATA_REQUIRED: public.awards.award_year contains NULL values. Provide operator-verified award_year data before starting the backend.';
        END IF;

        ALTER TABLE public.awards ALTER COLUMN award_year SET NOT NULL;
    END IF;
END;
$$;
