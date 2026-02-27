-- V80: Align hostrating column type with JPA Double mapping.

DECLARE
    v_data_type VARCHAR2(30);
BEGIN
    SELECT data_type
      INTO v_data_type
      FROM user_tab_cols
     WHERE table_name = 'PARTIES'
       AND column_name = 'HOSTRATING';

    IF v_data_type IS NOT NULL AND v_data_type <> 'FLOAT' THEN
        EXECUTE IMMEDIATE 'ALTER TABLE parties MODIFY (hostrating FLOAT(53))';
    END IF;
END;
/
