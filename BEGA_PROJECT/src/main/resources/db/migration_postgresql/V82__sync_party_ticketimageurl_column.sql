-- V82: Ensure parties.ticketimageurl exists for Party.ticketImageUrl mapping.
-- PostgreSQL equivalent of Oracle V82.

ALTER TABLE IF EXISTS parties
    ADD COLUMN IF NOT EXISTS ticketimageurl VARCHAR(500);

DO $$
BEGIN
    IF to_regclass('public.parties') IS NULL THEN
        RETURN;
    END IF;

    -- Backfill from ticket_image_url if it exists
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'parties' AND column_name = 'ticket_image_url'
    ) THEN
        EXECUTE 'UPDATE parties SET ticketimageurl = ticket_image_url WHERE ticketimageurl IS NULL AND ticket_image_url IS NOT NULL';
    END IF;
END;
$$;
