DECLARE
    v_column_exists NUMBER := 0;
    v_index_exists NUMBER := 0;
BEGIN
    SELECT COUNT(*) INTO v_column_exists
      FROM user_tab_columns
     WHERE table_name = 'MEDIA_ASSETS'
       AND column_name = 'FINALIZE_TOKEN';
    IF v_column_exists = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE media_assets ADD (finalize_token VARCHAR2(36))';
    END IF;

    SELECT COUNT(*) INTO v_column_exists
      FROM user_tab_columns
     WHERE table_name = 'MEDIA_ASSETS'
       AND column_name = 'FINALIZE_LEASE_UNTIL';
    IF v_column_exists = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE media_assets ADD (finalize_lease_until TIMESTAMP(6))';
    END IF;

    SELECT COUNT(*) INTO v_column_exists
      FROM user_tab_columns
     WHERE table_name = 'MEDIA_ASSETS'
       AND column_name = 'FINALIZE_OBJECT_KEY';
    IF v_column_exists = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE media_assets ADD (finalize_object_key VARCHAR2(2048))';
    END IF;

    SELECT COUNT(*) INTO v_column_exists
      FROM user_tab_columns
     WHERE table_name = 'MEDIA_ASSETS'
       AND column_name = 'FINALIZE_FEED_OBJECT_KEY';
    IF v_column_exists = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE media_assets ADD (finalize_feed_object_key VARCHAR2(2048))';
    END IF;

    SELECT COUNT(*) INTO v_column_exists
      FROM user_tab_columns
     WHERE table_name = 'MEDIA_ASSETS'
       AND column_name = 'FINALIZE_CLEANUP_VERIFIED_AT';
    IF v_column_exists = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE media_assets ADD (finalize_cleanup_verified_at TIMESTAMP(6))';
    END IF;

    SELECT COUNT(*) INTO v_index_exists
      FROM user_indexes
     WHERE index_name = 'IDX_MEDIA_ASSET_FINALIZE_LEASE';
    IF v_index_exists = 0 THEN
        EXECUTE IMMEDIATE 'CREATE INDEX idx_media_asset_finalize_lease ON media_assets (status, finalize_lease_until, upload_expires_at)';
    END IF;
END;
/

DECLARE
    v_table_exists NUMBER := 0;
    v_index_exists NUMBER := 0;
BEGIN
    SELECT COUNT(*) INTO v_table_exists
      FROM user_tables
     WHERE table_name = 'MEDIA_FINALIZE_ATTEMPTS';
    IF v_table_exists = 0 THEN
        EXECUTE IMMEDIATE '
            CREATE TABLE media_finalize_attempts (
                attempt_id VARCHAR2(36) NOT NULL,
                media_asset_id NUMBER(19) NOT NULL,
                media_domain VARCHAR2(30) NOT NULL,
                bucket_name VARCHAR2(255) NOT NULL,
                object_key VARCHAR2(2048),
                feed_object_key VARCHAR2(2048),
                state VARCHAR2(32) NOT NULL,
                lease_expires_at TIMESTAMP(6),
                next_cleanup_at TIMESTAMP(6),
                cleanup_token VARCHAR2(36),
                cleanup_lease_until TIMESTAMP(6),
                cleanup_attempt_count NUMBER(10) DEFAULT 0 NOT NULL,
                created_at TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
                updated_at TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
                CONSTRAINT pk_media_finalize_attempts PRIMARY KEY (attempt_id),
                CONSTRAINT fk_mfa_asset FOREIGN KEY (media_asset_id)
                    REFERENCES media_assets (id)
            )';
    END IF;

    SELECT COUNT(*) INTO v_index_exists
      FROM user_indexes
     WHERE index_name = 'IDX_MFA_CLEANUP_DUE';
    IF v_index_exists = 0 THEN
        EXECUTE IMMEDIATE 'CREATE INDEX idx_mfa_cleanup_due ON media_finalize_attempts (state, next_cleanup_at, cleanup_lease_until, attempt_id)';
    END IF;

    SELECT COUNT(*) INTO v_index_exists
      FROM user_indexes
     WHERE index_name = 'IDX_MFA_ASSET_STATE';
    IF v_index_exists = 0 THEN
        EXECUTE IMMEDIATE 'CREATE INDEX idx_mfa_asset_state ON media_finalize_attempts (media_asset_id, state, created_at, attempt_id)';
    END IF;
END;
/
