-- V84: Ensure password_reset_tokens.expirydate exists for Hibernate schema validation.

DECLARE
    v_has_expirydate NUMBER := 0;
    v_has_expiry_date NUMBER := 0;
    v_has_expiry NUMBER := 0;
BEGIN
    SELECT COUNT(*)
      INTO v_has_expirydate
      FROM user_tab_columns
     WHERE table_name = 'PASSWORD_RESET_TOKENS'
       AND column_name = 'EXPIRYDATE';

    IF v_has_expirydate = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE password_reset_tokens ADD (expirydate TIMESTAMP)';

        SELECT COUNT(*)
          INTO v_has_expiry_date
          FROM user_tab_columns
         WHERE table_name = 'PASSWORD_RESET_TOKENS'
           AND column_name = 'EXPIRY_DATE';

        IF v_has_expiry_date > 0 THEN
            EXECUTE IMMEDIATE 'UPDATE password_reset_tokens SET expirydate = expiry_date WHERE expirydate IS NULL';
        ELSE
            SELECT COUNT(*)
              INTO v_has_expiry
              FROM user_tab_columns
             WHERE table_name = 'PASSWORD_RESET_TOKENS'
               AND column_name = 'EXPIRY';

            IF v_has_expiry > 0 THEN
                EXECUTE IMMEDIATE 'UPDATE password_reset_tokens SET expirydate = expiry WHERE expirydate IS NULL';
            ELSE
                EXECUTE IMMEDIATE 'UPDATE password_reset_tokens SET expirydate = SYSTIMESTAMP WHERE expirydate IS NULL';
            END IF;
        END IF;

        EXECUTE IMMEDIATE 'ALTER TABLE password_reset_tokens MODIFY (expirydate TIMESTAMP NOT NULL)';
    ELSE
        SELECT COUNT(*)
          INTO v_has_expiry
          FROM user_tab_columns
         WHERE table_name = 'PASSWORD_RESET_TOKENS'
           AND column_name = 'EXPIRYDATE'
           AND nullable = 'Y';

        IF v_has_expiry > 0 THEN
            EXECUTE IMMEDIATE 'UPDATE password_reset_tokens SET expirydate = SYSTIMESTAMP WHERE expirydate IS NULL';
            EXECUTE IMMEDIATE 'ALTER TABLE password_reset_tokens MODIFY (expirydate TIMESTAMP NOT NULL)';
        END IF;
    END IF;
END;
/
