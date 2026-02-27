-- V105: Normalize cheer_post_reports.content to VARCHAR2(3000 CHAR)
-- to satisfy current Hibernate schema validation in prod.

DECLARE
    v_table_count NUMBER := 0;
    v_column_count NUMBER := 0;
    v_content_type VARCHAR2(128);
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
        ELSE
            SELECT data_type
              INTO v_content_type
              FROM user_tab_cols
             WHERE table_name = 'CHEER_POST_REPORTS'
               AND column_name = 'CONTENT';

            IF v_content_type = 'CLOB' OR v_content_type = 'NCLOB' THEN
                EXECUTE IMMEDIATE 'ALTER TABLE cheer_post_reports ADD content_varchar VARCHAR2(3000 CHAR)';
                EXECUTE IMMEDIATE 'UPDATE cheer_post_reports SET content_varchar = DBMS_LOB.SUBSTR(content, 3000, 1)';
                EXECUTE IMMEDIATE 'ALTER TABLE cheer_post_reports DROP COLUMN content';
                EXECUTE IMMEDIATE 'ALTER TABLE cheer_post_reports RENAME COLUMN content_varchar TO content';
            END IF;
        END IF;
    END IF;
END;
/
