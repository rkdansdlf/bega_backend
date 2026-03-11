-- Reconcile duplicated expiry columns on password_reset_tokens.
-- Keep expirydate as the canonical column and remove legacy expiry_date.

DO $$
BEGIN
    IF to_regclass('public.password_reset_tokens') IS NULL THEN
        RETURN;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'password_reset_tokens'
          AND column_name = 'expiry_date'
    ) THEN
        IF NOT EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = 'public'
              AND table_name = 'password_reset_tokens'
              AND column_name = 'expirydate'
        ) THEN
            EXECUTE 'ALTER TABLE password_reset_tokens ADD COLUMN expirydate TIMESTAMP';
        END IF;

        EXECUTE '
            UPDATE password_reset_tokens
               SET expirydate = COALESCE(expirydate, expiry_date),
                   expiry_date = COALESCE(expiry_date, expirydate)
        ';

        EXECUTE '
            UPDATE password_reset_tokens
               SET expirydate = CURRENT_TIMESTAMP
             WHERE expirydate IS NULL
        ';

        EXECUTE 'ALTER TABLE password_reset_tokens ALTER COLUMN expirydate SET NOT NULL';
        EXECUTE 'ALTER TABLE password_reset_tokens DROP COLUMN expiry_date';
    END IF;
END;
$$;
