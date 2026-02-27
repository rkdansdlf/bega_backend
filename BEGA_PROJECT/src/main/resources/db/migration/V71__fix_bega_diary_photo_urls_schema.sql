DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*)
    INTO v_count
    FROM user_tables
    WHERE table_name = 'BEGA_DIARY_PHOTO_URLS';

    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE TABLE bega_diary_photo_urls (
            bega_diary_id NUMBER(19) NOT NULL,
            photo_url VARCHAR2(2048) NOT NULL,
            CONSTRAINT pk_bega_diary_photo_urls PRIMARY KEY (bega_diary_id, photo_url),
            CONSTRAINT fk_bega_diary_photo_urls_bega_diary FOREIGN KEY (bega_diary_id)
                REFERENCES bega_diary(id) ON DELETE CASCADE
        )';

        EXECUTE IMMEDIATE 'CREATE INDEX idx_bega_diary_photo_urls_begadiary_id
            ON bega_diary_photo_urls (bega_diary_id)';
        RETURN;
    END IF;

    SELECT COUNT(*)
    INTO v_count
    FROM user_tab_columns
    WHERE table_name = 'BEGA_DIARY_PHOTO_URLS'
    AND column_name = 'PHOTO_URL';

    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE bega_diary_photo_urls ADD (photo_url VARCHAR2(2048))';
    END IF;

    SELECT COUNT(*)
    INTO v_count
    FROM user_tab_columns
    WHERE table_name = 'BEGA_DIARY_PHOTO_URLS'
    AND column_name = 'URL';

    IF v_count > 0 THEN
        EXECUTE IMMEDIATE 'UPDATE bega_diary_photo_urls SET photo_url = url WHERE photo_url IS NULL';
    END IF;

    SELECT COUNT(*)
    INTO v_count
    FROM user_tab_columns
    WHERE table_name = 'BEGA_DIARY_PHOTO_URLS'
    AND column_name = 'PHOTOURL';

    IF v_count > 0 THEN
        EXECUTE IMMEDIATE 'UPDATE bega_diary_photo_urls SET photo_url = photourl WHERE photo_url IS NULL';
    END IF;

    SELECT COUNT(*)
    INTO v_count
    FROM user_tab_columns
    WHERE table_name = 'BEGA_DIARY_PHOTO_URLS'
    AND column_name = 'PHOTO_URLS';

    IF v_count > 0 THEN
        EXECUTE IMMEDIATE 'UPDATE bega_diary_photo_urls SET photo_url = photo_urls WHERE photo_url IS NULL';
    END IF;

    EXECUTE IMMEDIATE 'UPDATE bega_diary_photo_urls SET photo_url = '''' WHERE photo_url IS NULL';
    EXECUTE IMMEDIATE 'ALTER TABLE bega_diary_photo_urls MODIFY photo_url VARCHAR2(2048) NOT NULL';

    SELECT COUNT(*)
    INTO v_count
    FROM user_indexes
    WHERE index_name = 'IDX_BEGA_DIARY_PHOTO_URLS_BEGADIARY_ID';

    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE INDEX idx_bega_diary_photo_urls_begadiary_id
            ON bega_diary_photo_urls (bega_diary_id)';
    END IF;
END;
/
