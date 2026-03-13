-- V73: Ensure legacy parties timestamp columns exist and sync with underscored variants.
-- Some environments keep created_at/updated_at, while the Party entity maps createdat/updatedat.

DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*)
      INTO v_count
      FROM user_tab_cols
     WHERE table_name = 'PARTIES'
       AND column_name = 'CREATEDAT';

    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE parties ADD (createdat TIMESTAMP(6) WITH TIME ZONE)';

        SELECT COUNT(*)
          INTO v_count
          FROM user_tab_cols
         WHERE table_name = 'PARTIES'
           AND column_name = 'CREATED_AT';

        IF v_count > 0 THEN
            EXECUTE IMMEDIATE 'UPDATE parties SET createdat = created_at WHERE createdat IS NULL';
        ELSE
            EXECUTE IMMEDIATE 'UPDATE parties SET createdat = SYSTIMESTAMP WHERE createdat IS NULL';
        END IF;

        EXECUTE IMMEDIATE 'ALTER TABLE parties MODIFY (createdat TIMESTAMP(6) WITH TIME ZONE NOT NULL)';
        EXECUTE IMMEDIATE 'ALTER TABLE parties MODIFY (createdat DEFAULT SYSTIMESTAMP)';
    ELSE
        SELECT COUNT(*)
          INTO v_count
          FROM user_tab_cols
         WHERE table_name = 'PARTIES'
           AND column_name = 'UPDATED_AT';

        IF v_count > 0 THEN
            EXECUTE IMMEDIATE 'UPDATE parties SET createdat = created_at WHERE createdat IS NULL AND created_at IS NOT NULL';
        END IF;
    END IF;

    SELECT COUNT(*)
      INTO v_count
      FROM user_tab_cols
     WHERE table_name = 'PARTIES'
       AND column_name = 'UPDATEDAT';

    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE parties ADD (updatedat TIMESTAMP(6) WITH TIME ZONE)';

        SELECT COUNT(*)
          INTO v_count
          FROM user_tab_cols
         WHERE table_name = 'PARTIES'
           AND column_name = 'UPDATED_AT';

        IF v_count > 0 THEN
            EXECUTE IMMEDIATE 'UPDATE parties SET updatedat = updated_at WHERE updatedat IS NULL';
        ELSE
            EXECUTE IMMEDIATE 'UPDATE parties SET updatedat = createdat WHERE updatedat IS NULL';
            EXECUTE IMMEDIATE 'UPDATE parties SET updatedat = SYSTIMESTAMP WHERE updatedat IS NULL';
        END IF;

        EXECUTE IMMEDIATE 'ALTER TABLE parties MODIFY (updatedat TIMESTAMP(6) WITH TIME ZONE NOT NULL)';
        EXECUTE IMMEDIATE 'ALTER TABLE parties MODIFY (updatedat DEFAULT SYSTIMESTAMP)';
    ELSE
        SELECT COUNT(*)
          INTO v_count
          FROM user_tab_cols
         WHERE table_name = 'PARTIES'
           AND column_name = 'UPDATED_AT';

        IF v_count > 0 THEN
            EXECUTE IMMEDIATE 'UPDATE parties SET updatedat = updated_at WHERE updatedat IS NULL AND updated_at IS NOT NULL';
        END IF;
    END IF;
END;
/
