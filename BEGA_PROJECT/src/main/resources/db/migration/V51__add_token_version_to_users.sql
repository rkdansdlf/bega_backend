-- V51: User token version for JWT invalidation
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count
    FROM user_tab_columns
    WHERE table_name = 'USERS'
      AND column_name = 'TOKEN_VERSION';

    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE users ADD token_version NUMBER(10)';
        EXECUTE IMMEDIATE 'UPDATE users SET token_version = 0 WHERE token_version IS NULL';
        EXECUTE IMMEDIATE 'ALTER TABLE users MODIFY token_version NUMBER(10) DEFAULT 0 NOT NULL';
    END IF;
END;
/
