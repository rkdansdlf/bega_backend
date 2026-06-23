-- V156: Optimize payment compensation reconciliation lookup path (PostgreSQL).

DO $$
BEGIN
    IF to_regclass('public.payment_intents') IS NULL THEN
        RETURN;
    END IF;

    IF NOT EXISTS (
        SELECT 1
          FROM pg_indexes
         WHERE schemaname = 'public'
           AND tablename = 'payment_intents'
           AND indexname = 'idx_payment_intents_status_updated_at'
    ) THEN
        CREATE INDEX idx_payment_intents_status_updated_at
            ON public.payment_intents (status, updated_at);
    END IF;
END $$;
