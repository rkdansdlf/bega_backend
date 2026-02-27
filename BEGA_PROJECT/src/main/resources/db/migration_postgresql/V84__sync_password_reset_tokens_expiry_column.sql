-- V84: Ensure password_reset_tokens.expirydate exists and is NOT NULL.
-- PostgreSQL equivalent of Oracle V84.
-- V52 already creates this column; this migration is effectively a no-op on fresh envs.

ALTER TABLE IF EXISTS password_reset_tokens
    ADD COLUMN IF NOT EXISTS expirydate TIMESTAMP;

DO $$
BEGIN
    IF to_regclass('public.password_reset_tokens') IS NULL THEN
        RETURN;
    END IF;

    -- Backfill from expiry_date if it exists
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'password_reset_tokens' AND column_name = 'expiry_date'
    ) THEN
        EXECUTE 'UPDATE password_reset_tokens SET expirydate = expiry_date WHERE expirydate IS NULL AND expiry_date IS NOT NULL';
    ELSIF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'password_reset_tokens' AND column_name = 'expiry'
    ) THEN
        EXECUTE 'UPDATE password_reset_tokens SET expirydate = expiry WHERE expirydate IS NULL AND expiry IS NOT NULL';
    ELSE
        EXECUTE 'UPDATE password_reset_tokens SET expirydate = CURRENT_TIMESTAMP WHERE expirydate IS NULL';
    END IF;

    -- Ensure NOT NULL
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'password_reset_tokens'
          AND column_name = 'expirydate'
          AND is_nullable = 'YES'
    ) THEN
        EXECUTE 'UPDATE password_reset_tokens SET expirydate = CURRENT_TIMESTAMP WHERE expirydate IS NULL';
        EXECUTE 'ALTER TABLE password_reset_tokens ALTER COLUMN expirydate SET NOT NULL';
    END IF;
END;
$$;
