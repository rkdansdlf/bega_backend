DECLARE
    e_column_exists EXCEPTION;
    PRAGMA EXCEPTION_INIT(e_column_exists, -1430); -- ORA-01430
BEGIN
    BEGIN
        EXECUTE IMMEDIATE 'ALTER TABLE cheer_post ADD (diary_id NUMBER(19))';
    EXCEPTION
        WHEN e_column_exists THEN NULL;
    END;

    BEGIN
        EXECUTE IMMEDIATE 'ALTER TABLE cheer_post ADD (party_id NUMBER(19))';
    EXCEPTION
        WHEN e_column_exists THEN NULL;
    END;
END;
/

DECLARE
    e_constraint_exists EXCEPTION;
    PRAGMA EXCEPTION_INIT(e_constraint_exists, -2264); -- ORA-02264
BEGIN
    BEGIN
        EXECUTE IMMEDIATE 'ALTER TABLE cheer_post ADD CONSTRAINT fk_cheer_post_diary FOREIGN KEY (diary_id) REFERENCES bega_diary(id) ON DELETE SET NULL';
    EXCEPTION
        WHEN e_constraint_exists THEN NULL;
    END;

    BEGIN
        EXECUTE IMMEDIATE 'ALTER TABLE cheer_post ADD CONSTRAINT fk_cheer_post_party FOREIGN KEY (party_id) REFERENCES parties(id) ON DELETE SET NULL';
    EXCEPTION
        WHEN e_constraint_exists THEN NULL;
    END;

    BEGIN
        EXECUTE IMMEDIATE q'[
            ALTER TABLE cheer_post ADD CONSTRAINT ck_cheer_post_link_type CHECK (
                (posttype IN ('NORMAL', 'NOTICE') AND diary_id IS NULL AND party_id IS NULL)
                OR (posttype = 'CHECKIN' AND party_id IS NULL)
                OR (posttype = 'RECRUITMENT' AND diary_id IS NULL)
            )
        ]';
    EXCEPTION
        WHEN e_constraint_exists THEN NULL;
    END;
END;
/

DECLARE
    e_index_exists EXCEPTION;
    PRAGMA EXCEPTION_INIT(e_index_exists, -955); -- ORA-00955
BEGIN
    BEGIN
        EXECUTE IMMEDIATE 'CREATE INDEX idx_cheer_post_diary ON cheer_post(diary_id)';
    EXCEPTION
        WHEN e_index_exists THEN NULL;
    END;

    BEGIN
        EXECUTE IMMEDIATE 'CREATE INDEX idx_cheer_post_party ON cheer_post(party_id)';
    EXCEPTION
        WHEN e_index_exists THEN NULL;
    END;

    BEGIN
        EXECUTE IMMEDIATE 'CREATE UNIQUE INDEX uq_cheer_post_active_diary ON cheer_post(CASE WHEN NVL(deleted, 0) = 0 AND diary_id IS NOT NULL THEN diary_id END)';
    EXCEPTION
        WHEN e_index_exists THEN NULL;
    END;

    BEGIN
        EXECUTE IMMEDIATE 'CREATE UNIQUE INDEX uq_cheer_post_active_party ON cheer_post(CASE WHEN NVL(deleted, 0) = 0 AND party_id IS NOT NULL THEN party_id END)';
    EXCEPTION
        WHEN e_index_exists THEN NULL;
    END;
END;
/
