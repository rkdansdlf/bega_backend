-- V52: repost policy schema alignment (Oracle)
-- - Add share/source metadata columns to cheer_post
-- - Add moderation workflow columns to cheer_post_reports

DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM user_tab_columns WHERE table_name = 'CHEER_POST' AND column_name = 'SHARE_MODE';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE cheer_post ADD share_mode VARCHAR2(24)';
    END IF;

    SELECT COUNT(*) INTO v_count FROM user_tab_columns WHERE table_name = 'CHEER_POST' AND column_name = 'SOURCE_URL';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE cheer_post ADD source_url VARCHAR2(1024)';
    END IF;

    SELECT COUNT(*) INTO v_count FROM user_tab_columns WHERE table_name = 'CHEER_POST' AND column_name = 'SOURCE_TITLE';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE cheer_post ADD source_title VARCHAR2(300)';
    END IF;

    SELECT COUNT(*) INTO v_count FROM user_tab_columns WHERE table_name = 'CHEER_POST' AND column_name = 'SOURCE_AUTHOR';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE cheer_post ADD source_author VARCHAR2(200)';
    END IF;

    SELECT COUNT(*) INTO v_count FROM user_tab_columns WHERE table_name = 'CHEER_POST' AND column_name = 'SOURCE_LICENSE';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE cheer_post ADD source_license VARCHAR2(120)';
    END IF;

    SELECT COUNT(*) INTO v_count FROM user_tab_columns WHERE table_name = 'CHEER_POST' AND column_name = 'SOURCE_LICENSE_URL';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE cheer_post ADD source_license_url VARCHAR2(1024)';
    END IF;

    SELECT COUNT(*) INTO v_count FROM user_tab_columns WHERE table_name = 'CHEER_POST' AND column_name = 'SOURCE_CHANGED_NOTE';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE cheer_post ADD source_changed_note VARCHAR2(1200)';
    END IF;

    SELECT COUNT(*) INTO v_count FROM user_tab_columns WHERE table_name = 'CHEER_POST' AND column_name = 'SOURCE_SNAPSHOT_TYPE';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE cheer_post ADD source_snapshot_type VARCHAR2(80)';
    END IF;

    EXECUTE IMMEDIATE 'UPDATE cheer_post SET share_mode = ''INTERNAL_REPOST'' WHERE share_mode IS NULL';

    SELECT COUNT(*) INTO v_count FROM user_tab_columns WHERE table_name = 'CHEER_POST_REPORTS' AND column_name = 'STATUS';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE cheer_post_reports ADD status VARCHAR2(24)';
    END IF;

    SELECT COUNT(*) INTO v_count FROM user_tab_columns WHERE table_name = 'CHEER_POST_REPORTS' AND column_name = 'ADMIN_ACTION';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE cheer_post_reports ADD admin_action VARCHAR2(30)';
    END IF;

    SELECT COUNT(*) INTO v_count FROM user_tab_columns WHERE table_name = 'CHEER_POST_REPORTS' AND column_name = 'ADMIN_MEMO';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE cheer_post_reports ADD admin_memo VARCHAR2(1000)';
    END IF;

    SELECT COUNT(*) INTO v_count FROM user_tab_columns WHERE table_name = 'CHEER_POST_REPORTS' AND column_name = 'HANDLED_BY';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE cheer_post_reports ADD handled_by NUMBER(19)';
    END IF;

    SELECT COUNT(*) INTO v_count FROM user_tab_columns WHERE table_name = 'CHEER_POST_REPORTS' AND column_name = 'HANDLED_AT';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE cheer_post_reports ADD handled_at TIMESTAMP';
    END IF;

    SELECT COUNT(*) INTO v_count FROM user_tab_columns WHERE table_name = 'CHEER_POST_REPORTS' AND column_name = 'EVIDENCE_URL';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE cheer_post_reports ADD evidence_url VARCHAR2(1024)';
    END IF;

    SELECT COUNT(*) INTO v_count FROM user_tab_columns WHERE table_name = 'CHEER_POST_REPORTS' AND column_name = 'REQUESTED_ACTION';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE cheer_post_reports ADD requested_action VARCHAR2(64)';
    END IF;

    SELECT COUNT(*) INTO v_count FROM user_tab_columns WHERE table_name = 'CHEER_POST_REPORTS' AND column_name = 'APPEAL_STATUS';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE cheer_post_reports ADD appeal_status VARCHAR2(24)';
    END IF;

    SELECT COUNT(*) INTO v_count FROM user_tab_columns WHERE table_name = 'CHEER_POST_REPORTS' AND column_name = 'APPEAL_REASON';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE cheer_post_reports ADD appeal_reason VARCHAR2(1200)';
    END IF;

    SELECT COUNT(*) INTO v_count FROM user_tab_columns WHERE table_name = 'CHEER_POST_REPORTS' AND column_name = 'APPEAL_COUNT';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE cheer_post_reports ADD appeal_count NUMBER(10)';
    END IF;

    EXECUTE IMMEDIATE 'UPDATE cheer_post_reports SET status = ''PENDING'' WHERE status IS NULL';
    EXECUTE IMMEDIATE 'UPDATE cheer_post_reports SET appeal_status = ''NONE'' WHERE appeal_status IS NULL';
    EXECUTE IMMEDIATE 'UPDATE cheer_post_reports SET appeal_count = 0 WHERE appeal_count IS NULL';
END;
/

DECLARE
    e_exists EXCEPTION;
    PRAGMA EXCEPTION_INIT(e_exists, -955);
BEGIN
    EXECUTE IMMEDIATE 'CREATE INDEX idx_cheer_post_source_url ON cheer_post(source_url)';
EXCEPTION
    WHEN e_exists THEN NULL;
END;
/

DECLARE
    e_exists EXCEPTION;
    PRAGMA EXCEPTION_INIT(e_exists, -955);
BEGIN
    EXECUTE IMMEDIATE 'CREATE INDEX idx_cpr_status_createdat ON cheer_post_reports(status, createdat DESC)';
EXCEPTION
    WHEN e_exists THEN NULL;
END;
/

DECLARE
    e_exists EXCEPTION;
    PRAGMA EXCEPTION_INIT(e_exists, -955);
BEGIN
    EXECUTE IMMEDIATE 'CREATE INDEX idx_cpr_reporter_post_createdat ON cheer_post_reports(reporter_id, post_id, createdat DESC)';
EXCEPTION
    WHEN e_exists THEN NULL;
END;
/
