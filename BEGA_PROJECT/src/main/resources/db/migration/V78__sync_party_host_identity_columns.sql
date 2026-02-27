-- V78: Ensure parties.hostid and host-specific identity columns exist.
-- Some environments use older/underscored column names.

DECLARE
    v_count NUMBER;
BEGIN
    -- hostid (required by Party.hostId)
    SELECT COUNT(*)
      INTO v_count
      FROM user_tab_cols
     WHERE table_name = 'PARTIES'
       AND column_name = 'HOSTID';

    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE parties ADD (hostid NUMBER)';

        SELECT COUNT(*)
          INTO v_count
          FROM user_tab_cols
         WHERE table_name = 'PARTIES'
           AND column_name = 'HOST_ID';

        IF v_count > 0 THEN
            EXECUTE IMMEDIATE 'UPDATE parties SET hostid = HOST_ID WHERE hostid IS NULL AND HOST_ID IS NOT NULL';
        END IF;

        EXECUTE IMMEDIATE 'UPDATE parties SET hostid = -1 WHERE hostid IS NULL';

        EXECUTE IMMEDIATE 'ALTER TABLE parties MODIFY (hostid NUMBER NOT NULL)';
        EXECUTE IMMEDIATE 'ALTER TABLE parties MODIFY (hostid DEFAULT -1)';
    END IF;

    -- hostname
    SELECT COUNT(*)
      INTO v_count
      FROM user_tab_cols
     WHERE table_name = 'PARTIES'
       AND column_name = 'HOSTNAME';

    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE parties ADD (hostname VARCHAR2(50))';

        SELECT COUNT(*)
          INTO v_count
          FROM user_tab_cols
         WHERE table_name = 'PARTIES'
           AND column_name = 'HOST_NAME';

        IF v_count > 0 THEN
            EXECUTE IMMEDIATE 'UPDATE parties SET hostname = HOST_NAME WHERE hostname IS NULL AND HOST_NAME IS NOT NULL';
        END IF;

        EXECUTE IMMEDIATE q'[UPDATE parties SET hostname = 'UNKNOWN' WHERE hostname IS NULL]';

        EXECUTE IMMEDIATE 'ALTER TABLE parties MODIFY (hostname VARCHAR2(50) NOT NULL)';
        EXECUTE IMMEDIATE 'ALTER TABLE parties MODIFY (hostname DEFAULT ''UNKNOWN'')';
    END IF;
END;
/
