-- Reconcile duplicated expiry columns on password_reset_tokens.
-- Keep expirydate as the canonical column and remove legacy expiry_date.

DECLARE
    v_has_table NUMBER := 0;
    v_has_expirydate NUMBER := 0;
    v_has_expiry_date NUMBER := 0;
    v_nullable VARCHAR2(1);
BEGIN
    SELECT COUNT(*)
      INTO v_has_table
      FROM user_tables
     WHERE table_name = 'PASSWORD_RESET_TOKENS';

    IF v_has_table = 0 THEN
        RETURN;
    END IF;

    SELECT COUNT(*)
      INTO v_has_expirydate
      FROM user_tab_columns
     WHERE table_name = 'PASSWORD_RESET_TOKENS'
       AND column_name = 'EXPIRYDATE';

    SELECT COUNT(*)
      INTO v_has_expiry_date
      FROM user_tab_columns
     WHERE table_name = 'PASSWORD_RESET_TOKENS'
       AND column_name = 'EXPIRY_DATE';

    IF v_has_expiry_date > 0 THEN
        IF v_has_expirydate = 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE password_reset_tokens ADD (expirydate TIMESTAMP)';
        END IF;

        EXECUTE IMMEDIATE '
            UPDATE password_reset_tokens
               SET expirydate = NVL(expirydate, expiry_date),
                   expiry_date = NVL(expiry_date, expirydate)
        ';

        EXECUTE IMMEDIATE '
            UPDATE password_reset_tokens
               SET expirydate = SYSTIMESTAMP
             WHERE expirydate IS NULL
        ';

        -- Only set NOT NULL if currently nullable
        SELECT nullable INTO v_nullable
          FROM user_tab_columns
         WHERE table_name = 'PASSWORD_RESET_TOKENS'
           AND column_name = 'EXPIRYDATE';

        IF v_nullable = 'Y' THEN
            EXECUTE IMMEDIATE 'ALTER TABLE password_reset_tokens MODIFY (expirydate TIMESTAMP NOT NULL)';
        END IF;

        EXECUTE IMMEDIATE 'ALTER TABLE password_reset_tokens DROP COLUMN expiry_date';
    END IF;
END;
/
