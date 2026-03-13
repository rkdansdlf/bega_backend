-- V90: Align team_franchises.metadata_json with LONGVARCHAR mapping.

DECLARE
    v_has_table NUMBER := 0;
    v_has_metadata_json NUMBER := 0;
    v_has_metadata_json_vc NUMBER := 0;
    v_has_metadata_json_clob NUMBER := 0;
    v_data_type VARCHAR2(128);
    v_max_len NUMBER := 0;
BEGIN
    SELECT COUNT(*)
      INTO v_has_table
      FROM user_tables
     WHERE table_name = 'TEAM_FRANCHISES';

    IF v_has_table = 0 THEN
        RETURN;
    END IF;

    SELECT COUNT(*)
      INTO v_has_metadata_json
      FROM user_tab_cols
     WHERE table_name = 'TEAM_FRANCHISES'
       AND column_name = 'METADATA_JSON';

    SELECT COUNT(*)
      INTO v_has_metadata_json_vc
      FROM user_tab_cols
     WHERE table_name = 'TEAM_FRANCHISES'
       AND column_name = 'METADATA_JSON_VC';

    SELECT COUNT(*)
      INTO v_has_metadata_json_clob
      FROM user_tab_cols
     WHERE table_name = 'TEAM_FRANCHISES'
       AND column_name = 'METADATA_JSON_CLOB_BAK';

    IF v_has_metadata_json = 1 THEN
        SELECT data_type
          INTO v_data_type
          FROM user_tab_cols
         WHERE table_name = 'TEAM_FRANCHISES'
           AND column_name = 'METADATA_JSON';

        IF v_data_type = 'CLOB' THEN
            IF v_has_metadata_json_vc = 0 THEN
                EXECUTE IMMEDIATE 'ALTER TABLE team_franchises ADD (metadata_json_vc VARCHAR2(32600 CHAR))';
                v_has_metadata_json_vc := 1;
            END IF;

            SELECT NVL(MAX(DBMS_LOB.GETLENGTH(metadata_json)), 0)
              INTO v_max_len
              FROM team_franchises;

            IF v_max_len > 32600 THEN
                RAISE_APPLICATION_ERROR(-20001, 'team_franchises.metadata_json exceeds 32600 chars; manual migration required');
            END IF;

            EXECUTE IMMEDIATE '
                UPDATE team_franchises
                   SET metadata_json_vc = DBMS_LOB.SUBSTR(metadata_json, 32600, 1)
                 WHERE metadata_json IS NOT NULL
                   AND metadata_json_vc IS NULL
            ';

            IF v_has_metadata_json_clob = 0 THEN
                EXECUTE IMMEDIATE 'ALTER TABLE team_franchises RENAME COLUMN metadata_json TO metadata_json_clob_bak';
            ELSE
                EXECUTE IMMEDIATE 'ALTER TABLE team_franchises DROP COLUMN metadata_json';
            END IF;

            EXECUTE IMMEDIATE 'ALTER TABLE team_franchises RENAME COLUMN metadata_json_vc TO metadata_json';
        END IF;
    ELSIF v_has_metadata_json = 0 AND v_has_metadata_json_vc = 1 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE team_franchises RENAME COLUMN metadata_json_vc TO metadata_json';
    END IF;
END;
/
