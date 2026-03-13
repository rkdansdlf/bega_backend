-- V101: Ensure cheer_post_reports.content exists for Oracle schema validation.

DECLARE
    v_table_count NUMBER := 0;
    v_column_count NUMBER := 0;
BEGIN
    SELECT COUNT(*)
      INTO v_table_count
      FROM user_tables
     WHERE table_name = 'CHEER_POST_REPORTS';

    IF v_table_count = 1 THEN
        SELECT COUNT(*)
          INTO v_column_count
          FROM user_tab_cols
         WHERE table_name = 'CHEER_POST_REPORTS'
           AND column_name = 'CONTENT';

        IF v_column_count = 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE cheer_post_reports ADD content VARCHAR2(3000 CHAR)';
        END IF;
    END IF;
END;
/
