-- V126: Add stable session identifiers to refresh tokens (Oracle)

DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*)
      INTO v_count
      FROM user_tab_columns
     WHERE table_name = 'REFRESH_TOKENS'
       AND column_name = 'SESSION_ID';

    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE refresh_tokens ADD (session_id VARCHAR2(64 CHAR))';
    END IF;
END;
/

BEGIN
    EXECUTE IMMEDIATE 'CREATE UNIQUE INDEX uq_refresh_tokens_session_id ON refresh_tokens(session_id)';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE != -955 THEN
            RAISE;
        END IF;
END;
/
