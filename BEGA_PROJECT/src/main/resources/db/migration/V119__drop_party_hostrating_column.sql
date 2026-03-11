-- V119: Drop legacy parties.hostrating after migrating public APIs to review summary fields.

DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*)
      INTO v_count
      FROM user_tab_cols
     WHERE table_name = 'PARTIES'
       AND column_name = 'HOSTRATING';

    IF v_count > 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE parties DROP COLUMN hostrating';
    END IF;
END;
/
