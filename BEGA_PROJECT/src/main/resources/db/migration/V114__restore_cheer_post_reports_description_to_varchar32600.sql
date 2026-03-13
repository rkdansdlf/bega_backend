-- V114: Restore cheer_post_reports.description to VARCHAR2(32600 CHAR)
-- because current Hibernate mapping expects LONGVARCHAR (VARCHAR2).

DECLARE
    v_table_count NUMBER := 0;
    v_column_count NUMBER := 0;
    v_data_type VARCHAR2(128);
    v_tmp_column VARCHAR2(128) := 'description_v114_tmp';
BEGIN
    SELECT COUNT(*)
      INTO v_table_count
      FROM user_tables
     WHERE table_name = 'CHEER_POST_REPORTS';

    IF v_table_count = 0 THEN
        RETURN;
    END IF;

    SELECT COUNT(*)
      INTO v_column_count
      FROM user_tab_cols
     WHERE table_name = 'CHEER_POST_REPORTS'
       AND column_name = 'DESCRIPTION';

    IF v_column_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE cheer_post_reports ADD description VARCHAR2(32600 CHAR)';
        RETURN;
    END IF;

    SELECT data_type
      INTO v_data_type
      FROM user_tab_cols
     WHERE table_name = 'CHEER_POST_REPORTS'
       AND column_name = 'DESCRIPTION';

    IF v_data_type = 'VARCHAR2' THEN
        RETURN;
    END IF;

    EXECUTE IMMEDIATE 'ALTER TABLE cheer_post_reports ADD ' || v_tmp_column || ' VARCHAR2(32600 CHAR)';

    IF v_data_type IN ('CLOB', 'NCLOB') THEN
        EXECUTE IMMEDIATE
            'UPDATE cheer_post_reports SET ' || v_tmp_column || ' = DBMS_LOB.SUBSTR(description, 32600, 1)';
    ELSE
        EXECUTE IMMEDIATE
            'UPDATE cheer_post_reports SET ' || v_tmp_column || ' = TO_CHAR(description)';
    END IF;

    EXECUTE IMMEDIATE 'ALTER TABLE cheer_post_reports DROP COLUMN description';
    EXECUTE IMMEDIATE 'ALTER TABLE cheer_post_reports RENAME COLUMN ' || v_tmp_column || ' TO description';
END;
/
