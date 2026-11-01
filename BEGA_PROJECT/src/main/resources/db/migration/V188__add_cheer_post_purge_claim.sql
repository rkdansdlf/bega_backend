DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM user_tab_columns WHERE table_name = 'CHEER_POST' AND column_name = 'PURGE_STARTED_AT';
    IF v_count = 0 THEN EXECUTE IMMEDIATE 'ALTER TABLE cheer_post ADD (purge_started_at TIMESTAMP(6))'; END IF;
    SELECT COUNT(*) INTO v_count FROM user_tab_columns WHERE table_name = 'CHEER_POST' AND column_name = 'PURGE_CLAIM_TOKEN';
    IF v_count = 0 THEN EXECUTE IMMEDIATE 'ALTER TABLE cheer_post ADD (purge_claim_token VARCHAR2(36))'; END IF;
    SELECT COUNT(*) INTO v_count FROM user_tab_columns WHERE table_name = 'CHEER_POST' AND column_name = 'PURGE_LEASE_UNTIL';
    IF v_count = 0 THEN EXECUTE IMMEDIATE 'ALTER TABLE cheer_post ADD (purge_lease_until TIMESTAMP(6))'; END IF;
    SELECT COUNT(*) INTO v_count FROM user_tab_columns WHERE table_name = 'CHEER_POST' AND column_name = 'PURGE_RETRY_AT';
    IF v_count = 0 THEN EXECUTE IMMEDIATE 'ALTER TABLE cheer_post ADD (purge_retry_at TIMESTAMP(6))'; END IF;
    SELECT COUNT(*) INTO v_count FROM user_tab_columns WHERE table_name = 'CHEER_POST' AND column_name = 'PURGE_ATTEMPT_COUNT';
    IF v_count = 0 THEN EXECUTE IMMEDIATE 'ALTER TABLE cheer_post ADD (purge_attempt_count NUMBER(10) DEFAULT 0 NOT NULL)'; END IF;
    SELECT COUNT(*) INTO v_count FROM user_indexes WHERE index_name = 'IDX_CHEER_POST_PURGE_DUE';
    IF v_count = 0 THEN EXECUTE IMMEDIATE 'CREATE INDEX idx_cheer_post_purge_due ON cheer_post (deleted, purge_retry_at, purge_lease_until, id)'; END IF;
    SELECT COUNT(*) INTO v_count FROM user_tab_columns WHERE table_name = 'USERS' AND column_name = 'CONTENT_ANONYMIZATION_RETRY_AT';
    IF v_count = 0 THEN EXECUTE IMMEDIATE 'ALTER TABLE users ADD (content_anonymization_retry_at TIMESTAMP(6))'; END IF;
    SELECT COUNT(*) INTO v_count FROM user_indexes WHERE index_name = 'IDX_USER_CONTENT_ANON_RETRY';
    IF v_count = 0 THEN EXECUTE IMMEDIATE 'CREATE INDEX idx_user_content_anon_retry ON users (content_anonymization_retry_at, id)'; END IF;
END;
/
