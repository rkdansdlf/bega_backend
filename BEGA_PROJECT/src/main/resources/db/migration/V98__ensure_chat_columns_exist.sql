-- V98: Ensure chat image/read-tracking columns exist for Oracle deployments.

DECLARE
    v_table_count NUMBER := 0;
    v_column_count NUMBER := 0;
    v_legacy_created_count NUMBER := 0;
BEGIN
    SELECT COUNT(*)
      INTO v_table_count
      FROM user_tables
     WHERE table_name = 'CHAT_MESSAGES';

    IF v_table_count = 1 THEN
        SELECT COUNT(*)
          INTO v_column_count
          FROM user_tab_cols
         WHERE table_name = 'CHAT_MESSAGES'
           AND column_name = 'IMAGE_URL';

        IF v_column_count = 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE chat_messages ADD image_url VARCHAR2(2048 CHAR)';
        END IF;

        SELECT COUNT(*)
          INTO v_column_count
          FROM user_tab_cols
         WHERE table_name = 'CHAT_MESSAGES'
           AND column_name = 'CREATED_AT';

        IF v_column_count = 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE chat_messages ADD created_at TIMESTAMP(6) WITH TIME ZONE';
        END IF;

        SELECT COUNT(*)
          INTO v_legacy_created_count
          FROM user_tab_cols
         WHERE table_name = 'CHAT_MESSAGES'
           AND column_name = 'CREATEDAT';

        IF v_legacy_created_count = 1 THEN
            EXECUTE IMMEDIATE 'UPDATE chat_messages SET created_at = NVL(created_at, createdat) WHERE created_at IS NULL';
            EXECUTE IMMEDIATE 'UPDATE chat_messages SET createdat = NVL(createdat, created_at) WHERE createdat IS NULL';
            EXECUTE IMMEDIATE 'ALTER TABLE chat_messages MODIFY (createdat DEFAULT SYSTIMESTAMP)';
        END IF;

        EXECUTE IMMEDIATE 'UPDATE chat_messages SET created_at = SYSTIMESTAMP WHERE created_at IS NULL';
        EXECUTE IMMEDIATE 'ALTER TABLE chat_messages MODIFY (created_at TIMESTAMP(6) WITH TIME ZONE DEFAULT SYSTIMESTAMP)';
        SELECT COUNT(*)
          INTO v_column_count
          FROM user_tab_cols
         WHERE table_name = 'CHAT_MESSAGES'
           AND column_name = 'CREATED_AT'
           AND nullable = 'Y';

        IF v_column_count = 1 THEN
            BEGIN
                EXECUTE IMMEDIATE 'ALTER TABLE chat_messages MODIFY (created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL)';
            EXCEPTION
                WHEN OTHERS THEN
                    IF SQLCODE != -1442 THEN
                        RAISE;
                    END IF;
            END;
        END IF;
    END IF;

    SELECT COUNT(*)
      INTO v_table_count
      FROM user_tables
     WHERE table_name = 'PARTIES';

    IF v_table_count = 1 THEN
        SELECT COUNT(*)
          INTO v_column_count
          FROM user_tab_cols
         WHERE table_name = 'PARTIES'
           AND column_name = 'HOST_LAST_READ_CHAT_AT';

        IF v_column_count = 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE parties ADD host_last_read_chat_at TIMESTAMP(6) WITH TIME ZONE';
        END IF;
    END IF;

    SELECT COUNT(*)
      INTO v_table_count
      FROM user_tables
     WHERE table_name = 'PARTY_APPLICATIONS';

    IF v_table_count = 1 THEN
        SELECT COUNT(*)
          INTO v_column_count
          FROM user_tab_cols
         WHERE table_name = 'PARTY_APPLICATIONS'
           AND column_name = 'LAST_READ_CHAT_AT';

        IF v_column_count = 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE party_applications ADD last_read_chat_at TIMESTAMP(6) WITH TIME ZONE';
        END IF;
    END IF;
END;
/
