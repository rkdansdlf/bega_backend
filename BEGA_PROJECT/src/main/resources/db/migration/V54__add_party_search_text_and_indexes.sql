-- V54: parties 검색 최적화를 위한 search_text 컬럼 및 인덱스 추가 (Oracle)

DECLARE
    v_column_count NUMBER;
BEGIN
    SELECT COUNT(*)
      INTO v_column_count
      FROM user_tab_cols
     WHERE table_name = 'PARTIES'
       AND column_name = 'SEARCH_TEXT';

    IF v_column_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE parties ADD (search_text VARCHAR2(2000))';
    END IF;
END;
/

DECLARE
    v_expr CLOB;
    v_has_col NUMBER;
    v_sql CLOB;
BEGIN
    v_expr := '';

    FOR c IN (
        SELECT column_name, display_order
          FROM (
              SELECT 'STADIUM' AS column_name, 1 AS display_order FROM dual
              UNION ALL SELECT 'HOMETEAM', 2 FROM dual
              UNION ALL SELECT 'AWAYTEAM', 3 FROM dual
              UNION ALL SELECT 'SECTION', 4 FROM dual
              UNION ALL SELECT 'HOSTNAME', 5 FROM dual
              UNION ALL SELECT 'DESCRIPTION', 6 FROM dual
          )
         ORDER BY display_order
    ) LOOP
        SELECT COUNT(*)
          INTO v_has_col
          FROM user_tab_cols
         WHERE table_name = 'PARTIES'
           AND column_name = c.column_name;

        IF v_has_col > 0 THEN
            IF v_expr IS NOT NULL AND LENGTH(v_expr) > 0 THEN
                v_expr := v_expr || ' || '' '' || ';
            END IF;
            v_expr := v_expr || 'NVL(' || c.column_name || ', '''')';
        END IF;
    END LOOP;

    IF v_expr IS NULL OR LENGTH(v_expr) = 0 THEN
        v_expr := '''''';
    END IF;

    v_sql := 'UPDATE parties SET search_text = LOWER(TRIM(' || v_expr || ')) WHERE search_text IS NULL';
    EXECUTE IMMEDIATE v_sql;
END;
/

DECLARE
    e_index_exists EXCEPTION;
    PRAGMA EXCEPTION_INIT(e_index_exists, -955); -- ORA-00955
    e_already_indexed EXCEPTION;
    PRAGMA EXCEPTION_INIT(e_already_indexed, -1408); -- ORA-01408
    e_key_too_large EXCEPTION;
    PRAGMA EXCEPTION_INIT(e_key_too_large, -1450); -- ORA-01450
BEGIN
    BEGIN
        EXECUTE IMMEDIATE 'CREATE INDEX idx_parties_search_text ON parties(search_text)';
    EXCEPTION
        WHEN e_key_too_large THEN
            EXECUTE IMMEDIATE 'CREATE INDEX idx_parties_search_text_sub ON parties(SUBSTR(search_text, 1, 500))';
        WHEN e_index_exists OR e_already_indexed THEN
            NULL;
    END;

    BEGIN
        DECLARE
            v_status_col NUMBER;
            v_gamedate_col VARCHAR2(128);
        BEGIN
            SELECT COUNT(*)
              INTO v_status_col
              FROM user_tab_cols
             WHERE table_name = 'PARTIES'
               AND UPPER(column_name) = 'STATUS';

            BEGIN
                SELECT column_name
                  INTO v_gamedate_col
                  FROM (
                      SELECT column_name
                        FROM user_tab_cols
                       WHERE table_name = 'PARTIES'
                         AND (UPPER(column_name) = 'GAME_DATE' OR UPPER(column_name) = 'GAMEDATE')
                       ORDER BY CASE WHEN UPPER(column_name) = 'GAME_DATE' THEN 1 ELSE 2 END
                       )
                 WHERE ROWNUM = 1;
            EXCEPTION
                WHEN NO_DATA_FOUND THEN
                    v_gamedate_col := NULL;
            END;

            IF v_status_col > 0 AND v_gamedate_col IS NOT NULL THEN
                EXECUTE IMMEDIATE 'CREATE INDEX idx_parties_status_gamedate ON parties(status, ' || v_gamedate_col || ' DESC)';
            END IF;
        END;
    EXCEPTION
        WHEN e_index_exists OR e_already_indexed THEN
            NULL;
    END;
END;
/
