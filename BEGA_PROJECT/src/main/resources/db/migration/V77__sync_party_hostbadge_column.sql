-- V77: Ensure parties.hostbadge exists and sync with legacy variants.
-- Some environments were created with HOST_BADGE only.

DECLARE
    v_count        NUMBER;
    v_source_count NUMBER;
BEGIN
    SELECT COUNT(*)
      INTO v_count
      FROM user_tab_cols
     WHERE table_name = 'PARTIES'
       AND column_name = 'HOSTBADGE';

    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE parties ADD (hostbadge VARCHAR2(20))';

        SELECT COUNT(*)
          INTO v_source_count
          FROM user_tab_cols
         WHERE table_name = 'PARTIES'
           AND column_name = 'HOST_BADGE';

        IF v_source_count > 0 THEN
            EXECUTE IMMEDIATE 'UPDATE parties SET hostbadge = HOST_BADGE WHERE hostbadge IS NULL AND HOST_BADGE IS NOT NULL';
        END IF;

        EXECUTE IMMEDIATE 'UPDATE parties SET hostbadge = ''NEW'' WHERE hostbadge IS NULL';
        EXECUTE IMMEDIATE 'ALTER TABLE parties MODIFY (hostbadge VARCHAR2(20) NOT NULL)';
        EXECUTE IMMEDIATE 'ALTER TABLE parties MODIFY (hostbadge DEFAULT ''NEW'')';
    END IF;

    SELECT COUNT(*)
      INTO v_count
      FROM user_tab_cols
     WHERE table_name = 'PARTIES'
       AND column_name = 'HOSTBADGE'
       AND nullable = 'Y';

    IF v_count > 0 THEN
        EXECUTE IMMEDIATE 'UPDATE parties SET hostbadge = ''NEW'' WHERE hostbadge IS NULL';
        EXECUTE IMMEDIATE 'ALTER TABLE parties MODIFY (hostbadge VARCHAR2(20) NOT NULL)';
        EXECUTE IMMEDIATE 'ALTER TABLE parties MODIFY (hostbadge DEFAULT ''NEW'')';
    END IF;
END;
/
