-- V88: Ensure bega_diary seat columns exist for both legacy and current JPA mappings.
-- PostgreSQL equivalent of Oracle V88.
-- V71 already added seat_number / seat_row; this migration is a no-op on existing envs.

ALTER TABLE IF EXISTS bega_diary
    ADD COLUMN IF NOT EXISTS seat_number VARCHAR(50);

ALTER TABLE IF EXISTS bega_diary
    ADD COLUMN IF NOT EXISTS seat_row VARCHAR(50);

DO $$
BEGIN
    IF to_regclass('public.bega_diary') IS NULL THEN
        RETURN;
    END IF;

    -- Backfill seat_number from seatnumber
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'bega_diary' AND column_name = 'seatnumber'
    ) THEN
        EXECUTE 'UPDATE bega_diary SET seat_number = seatnumber WHERE seat_number IS NULL AND seatnumber IS NOT NULL';
    END IF;

    -- Backfill seat_row from seatrow
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'bega_diary' AND column_name = 'seatrow'
    ) THEN
        EXECUTE 'UPDATE bega_diary SET seat_row = seatrow WHERE seat_row IS NULL AND seatrow IS NOT NULL';
    END IF;
END;
$$;
