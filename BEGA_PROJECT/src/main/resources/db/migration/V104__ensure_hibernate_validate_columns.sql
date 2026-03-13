-- V101: Ensure Oracle columns required by current Hibernate mappings exist.

DECLARE
    v_table_count NUMBER := 0;
    v_column_count NUMBER := 0;
BEGIN
    SELECT COUNT(*)
      INTO v_table_count
      FROM user_tables
     WHERE table_name = 'CHEER_POST_REPORTS';

    IF v_table_count = 1 THEN
        SELECT COUNT(*)
          INTO v_column_count
          FROM user_tab_cols
         WHERE table_name = 'CHEER_POST_REPORTS'
           AND column_name = 'CONTENT';

        IF v_column_count = 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE cheer_post_reports ADD content CLOB';
        END IF;

        SELECT COUNT(*)
          INTO v_column_count
          FROM user_tab_cols
         WHERE table_name = 'CHEER_POST_REPORTS'
           AND column_name = 'DESCRIPTION';

        IF v_column_count = 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE cheer_post_reports ADD description CLOB';
        END IF;
    END IF;

    SELECT COUNT(*)
      INTO v_table_count
      FROM user_tables
     WHERE table_name = 'CHEER_POSTS';

    IF v_table_count = 1 THEN
        SELECT COUNT(*)
          INTO v_column_count
          FROM user_tab_cols
         WHERE table_name = 'CHEER_POSTS'
           AND column_name = 'CONTENT';

        IF v_column_count = 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE cheer_posts ADD content CLOB';
        END IF;
    END IF;

    SELECT COUNT(*)
      INTO v_table_count
      FROM user_tables
     WHERE table_name = 'CHEER_COMMENTS';

    IF v_table_count = 1 THEN
        SELECT COUNT(*)
          INTO v_column_count
          FROM user_tab_cols
         WHERE table_name = 'CHEER_COMMENTS'
           AND column_name = 'CONTENT';

        IF v_column_count = 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE cheer_comments ADD content CLOB';
        END IF;
    END IF;

    SELECT COUNT(*)
      INTO v_table_count
      FROM user_tables
     WHERE table_name = 'PLAYER_MOVEMENTS';

    IF v_table_count = 1 THEN
        SELECT COUNT(*)
          INTO v_column_count
          FROM user_tab_cols
         WHERE table_name = 'PLAYER_MOVEMENTS'
           AND column_name = 'DETAILS';

        IF v_column_count = 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE player_movements ADD details CLOB';
        END IF;
    END IF;

    SELECT COUNT(*)
      INTO v_table_count
      FROM user_tables
     WHERE table_name = 'MATE_SELLER_PAYOUT_PROFILES';

    IF v_table_count = 1 THEN
        SELECT COUNT(*)
          INTO v_column_count
          FROM user_tab_cols
         WHERE table_name = 'MATE_SELLER_PAYOUT_PROFILES'
           AND column_name = 'METADATA_JSON';

        IF v_column_count = 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE mate_seller_payout_profiles ADD metadata_json CLOB';
        END IF;
    END IF;

    SELECT COUNT(*)
      INTO v_table_count
      FROM user_tables
     WHERE table_name = 'RANKING_PREDICTIONS';

    IF v_table_count = 1 THEN
        SELECT COUNT(*)
          INTO v_column_count
          FROM user_tab_cols
         WHERE table_name = 'RANKING_PREDICTIONS'
           AND column_name = 'PREDICTION_DATA';

        IF v_column_count = 0 THEN
            SELECT COUNT(*)
              INTO v_column_count
              FROM user_tab_cols
             WHERE table_name = 'RANKING_PREDICTIONS'
               AND column_name = 'PREDICTIONDATA';

            IF v_column_count = 0 THEN
                EXECUTE IMMEDIATE 'ALTER TABLE ranking_predictions ADD prediction_data CLOB';
            END IF;
        END IF;
    END IF;
END;
/
