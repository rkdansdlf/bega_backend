-- V72: Align payment_intents pay_mode column with shared JPA mapping.
-- PostgreSQL legacy schema may still use mode.

ALTER TABLE IF EXISTS public.payment_intents
    ADD COLUMN IF NOT EXISTS pay_mode VARCHAR(20);

DO $$
BEGIN
    IF to_regclass('public.payment_intents') IS NULL THEN
        RETURN;
    END IF;

    IF EXISTS (
        SELECT 1
          FROM information_schema.columns
         WHERE table_schema = 'public'
           AND table_name = 'payment_intents'
           AND column_name = 'mode'
    ) THEN
        EXECUTE '
            UPDATE public.payment_intents
               SET pay_mode = mode
             WHERE pay_mode IS NULL
               AND mode IS NOT NULL
        ';
    END IF;

    EXECUTE '
        UPDATE public.payment_intents
           SET pay_mode = ''PREPARED''
         WHERE pay_mode IS NULL
    ';
END $$;

ALTER TABLE IF EXISTS public.payment_intents
    ALTER COLUMN pay_mode SET NOT NULL;
