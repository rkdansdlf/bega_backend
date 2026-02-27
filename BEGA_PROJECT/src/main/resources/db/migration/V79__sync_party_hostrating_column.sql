-- V79: Ensure parties.hostrating exists and has valid defaults for legacy rows.

DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*)
      INTO v_count
      FROM user_tab_cols
     WHERE table_name = 'PARTIES'
       AND column_name = 'HOSTRATING';

    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE parties ADD (hostrating NUMBER(3,2))';

        SELECT COUNT(*)
          INTO v_count
          FROM user_tab_cols
         WHERE table_name = 'PARTIES'
           AND column_name = 'HOST_RATING';

        IF v_count > 0 THEN
            EXECUTE IMMEDIATE 'UPDATE parties SET hostrating = HOST_RATING WHERE hostrating IS NULL AND HOST_RATING IS NOT NULL';
        END IF;

        EXECUTE IMMEDIATE 'UPDATE parties SET hostrating = 5.0 WHERE hostrating IS NULL';
        EXECUTE IMMEDIATE 'ALTER TABLE parties MODIFY (hostrating DEFAULT 5.0)';
        EXECUTE IMMEDIATE 'ALTER TABLE parties MODIFY (hostrating NUMBER(3,2) NOT NULL)';
    END IF;
END;
/
