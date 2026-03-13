-- V71: Ensure bega_diary seat columns required by current JPA mapping exist.
-- Legacy schemas may still use seatnumber/seatrow.

ALTER TABLE IF EXISTS public.bega_diary
    ADD COLUMN IF NOT EXISTS seat_number VARCHAR(50);

ALTER TABLE IF EXISTS public.bega_diary
    ADD COLUMN IF NOT EXISTS seat_row VARCHAR(50);

DO $$
BEGIN
    IF to_regclass('public.bega_diary') IS NULL THEN
        RETURN;
    END IF;

    IF EXISTS (
        SELECT 1
          FROM information_schema.columns
         WHERE table_schema = 'public'
           AND table_name = 'bega_diary'
           AND column_name = 'seatnumber'
    ) THEN
        EXECUTE '
            UPDATE public.bega_diary
               SET seat_number = seatnumber
             WHERE seat_number IS NULL
               AND seatnumber IS NOT NULL
        ';
    END IF;

    IF EXISTS (
        SELECT 1
          FROM information_schema.columns
         WHERE table_schema = 'public'
           AND table_name = 'bega_diary'
           AND column_name = 'seatrow'
    ) THEN
        EXECUTE '
            UPDATE public.bega_diary
               SET seat_row = seatrow
             WHERE seat_row IS NULL
               AND seatrow IS NOT NULL
        ';
    END IF;
END $$;
