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

UPDATE parties
   SET search_text = LOWER(
       TRIM(
            NVL(stadium, '') || ' ' ||
            NVL(hometeam, '') || ' ' ||
            NVL(awayteam, '') || ' ' ||
            NVL(section, '') || ' ' ||
            NVL(hostname, '') || ' ' ||
            NVL(description, '')
       )
   )
 WHERE search_text IS NULL;

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
        EXECUTE IMMEDIATE 'CREATE INDEX idx_parties_status_gamedate ON parties(status, gamedate DESC)';
    EXCEPTION
        WHEN e_index_exists OR e_already_indexed THEN
            NULL;
    END;
END;
/
