-- V89: Align payment_intents.pay_mode column with shared JPA mapping.
-- PostgreSQL equivalent of Oracle V89.
-- V72 already added pay_mode; this migration is a no-op on existing envs.

ALTER TABLE IF EXISTS payment_intents
    ADD COLUMN IF NOT EXISTS pay_mode VARCHAR(20);

DO $$
BEGIN
    IF to_regclass('public.payment_intents') IS NULL THEN
        RETURN;
    END IF;

    -- Backfill from mode if it exists (mode is a reserved word in PostgreSQL, quote it)
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'payment_intents' AND column_name = 'mode'
    ) THEN
        EXECUTE 'UPDATE payment_intents SET pay_mode = "mode" WHERE pay_mode IS NULL AND "mode" IS NOT NULL';
    END IF;

    EXECUTE 'UPDATE payment_intents SET pay_mode = ''PREPARED'' WHERE pay_mode IS NULL';

    -- Ensure NOT NULL
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'payment_intents'
          AND column_name = 'pay_mode'
          AND is_nullable = 'YES'
    ) THEN
        EXECUTE 'ALTER TABLE payment_intents ALTER COLUMN pay_mode SET NOT NULL';
    END IF;
END;
$$;
