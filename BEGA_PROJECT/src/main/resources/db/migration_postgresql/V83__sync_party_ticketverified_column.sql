-- V83: Ensure parties.ticketverified exists for Party.ticketVerified mapping.
-- PostgreSQL equivalent of Oracle V83. Uses BOOLEAN instead of NUMBER(1).

ALTER TABLE IF EXISTS parties
    ADD COLUMN IF NOT EXISTS ticketverified BOOLEAN;

DO $$
BEGIN
    IF to_regclass('public.parties') IS NULL THEN
        RETURN;
    END IF;

    EXECUTE 'UPDATE parties SET ticketverified = FALSE WHERE ticketverified IS NULL';
END;
$$;

ALTER TABLE IF EXISTS parties
    ALTER COLUMN ticketverified SET DEFAULT FALSE;

ALTER TABLE IF EXISTS parties
    ALTER COLUMN ticketverified SET NOT NULL;
