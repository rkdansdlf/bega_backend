DECLARE
    v_table_exists NUMBER := 0;
    v_column_exists NUMBER := 0;
    v_nullable VARCHAR2(1);
    v_index_exists NUMBER := 0;
BEGIN
    SELECT COUNT(*)
      INTO v_table_exists
      FROM user_tables
     WHERE table_name = 'SEAT_VIEW_PHOTO';

    IF v_table_exists > 0 THEN
        SELECT nullable
          INTO v_nullable
          FROM user_tab_cols
         WHERE table_name = 'SEAT_VIEW_PHOTO'
           AND column_name = 'DIARY_ID';

        IF v_nullable = 'N' THEN
            EXECUTE IMMEDIATE 'ALTER TABLE seat_view_photo MODIFY (diary_id NULL)';
        END IF;

        SELECT COUNT(*)
          INTO v_column_exists
          FROM user_tab_cols
         WHERE table_name = 'SEAT_VIEW_PHOTO'
           AND column_name = 'STADIUM';

        IF v_column_exists = 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE seat_view_photo ADD (stadium VARCHAR2(100))';
        END IF;

        SELECT COUNT(*)
          INTO v_column_exists
          FROM user_tab_cols
         WHERE table_name = 'SEAT_VIEW_PHOTO'
           AND column_name = 'SECTION';

        IF v_column_exists = 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE seat_view_photo ADD (section VARCHAR2(100))';
        END IF;

        SELECT COUNT(*)
          INTO v_column_exists
          FROM user_tab_cols
         WHERE table_name = 'SEAT_VIEW_PHOTO'
           AND column_name = 'BLOCK';

        IF v_column_exists = 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE seat_view_photo ADD (block VARCHAR2(100))';
        END IF;

        SELECT COUNT(*)
          INTO v_column_exists
          FROM user_tab_cols
         WHERE table_name = 'SEAT_VIEW_PHOTO'
           AND column_name = 'SEAT_ROW';

        IF v_column_exists = 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE seat_view_photo ADD (seat_row VARCHAR2(100))';
        END IF;

        SELECT COUNT(*)
          INTO v_column_exists
          FROM user_tab_cols
         WHERE table_name = 'SEAT_VIEW_PHOTO'
           AND column_name = 'SEAT_NUMBER';

        IF v_column_exists = 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE seat_view_photo ADD (seat_number VARCHAR2(100))';
        END IF;

        SELECT COUNT(*)
          INTO v_column_exists
          FROM user_tab_cols
         WHERE table_name = 'SEAT_VIEW_PHOTO'
           AND column_name = 'RATING';

        IF v_column_exists = 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE seat_view_photo ADD (rating NUMBER(1))';
        END IF;

        SELECT COUNT(*)
          INTO v_column_exists
          FROM user_tab_cols
         WHERE table_name = 'SEAT_VIEW_PHOTO'
           AND column_name = 'COMMENT_TEXT';

        IF v_column_exists = 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE seat_view_photo ADD (comment_text VARCHAR2(140))';
        END IF;

        SELECT COUNT(*)
          INTO v_column_exists
          FROM user_tab_cols
         WHERE table_name = 'SEAT_VIEW_PHOTO'
           AND column_name = 'TAGS_JSON';

        IF v_column_exists = 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE seat_view_photo ADD (tags_json VARCHAR2(1000))';
        END IF;

        SELECT COUNT(*)
          INTO v_index_exists
          FROM user_indexes
         WHERE index_name = 'IDX_SEAT_VIEW_PHOTO_DIRECT';

        IF v_index_exists = 0 THEN
            EXECUTE IMMEDIATE 'CREATE INDEX idx_seat_view_photo_direct ON seat_view_photo (stadium, section, moderation_status, admin_label, user_selected)';
        END IF;
    END IF;
END;
/
