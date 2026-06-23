-- V150: Guard home bootstrap and auth lookup paths against migration drift on Oracle.

DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*)
      INTO v_count
      FROM user_tables
     WHERE table_name = 'GAME';

    IF v_count > 0 THEN
        SELECT COUNT(*)
          INTO v_count
          FROM user_indexes
         WHERE table_name = 'GAME'
           AND index_name = 'IDX_GAME_HOME_SCHEDULED_WINDOW';

        IF v_count = 0 THEN
            EXECUTE IMMEDIATE
                'CREATE INDEX idx_game_home_scheduled_window
                    ON game(game_date, UPPER(game_status), is_dummy, game_id)';
        END IF;

        SELECT COUNT(*)
          INTO v_count
          FROM user_ind_columns
         WHERE table_name = 'GAME'
           AND column_name = 'GAME_DATE'
           AND column_position = 1;

        IF v_count = 0 THEN
            EXECUTE IMMEDIATE 'CREATE INDEX idx_game_date_lookup ON game(game_date)';
        END IF;
    END IF;
END;
/

DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*)
      INTO v_count
      FROM user_tables
     WHERE table_name = 'REFRESH_TOKENS';

    IF v_count > 0 THEN
        SELECT COUNT(*)
          INTO v_count
          FROM user_ind_columns
         WHERE table_name = 'REFRESH_TOKENS'
           AND column_name = 'EMAIL'
           AND column_position = 1;

        IF v_count = 0 THEN
            EXECUTE IMMEDIATE 'CREATE INDEX idx_refresh_tokens_email_lookup ON refresh_tokens(email)';
        END IF;

        SELECT COUNT(*)
          INTO v_count
          FROM user_ind_columns
         WHERE table_name = 'REFRESH_TOKENS'
           AND column_name = 'TOKEN'
           AND column_position = 1;

        IF v_count = 0 THEN
            EXECUTE IMMEDIATE 'CREATE INDEX idx_refresh_tokens_token_lookup ON refresh_tokens(token)';
        END IF;
    END IF;
END;
/

DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*)
      INTO v_count
      FROM user_tables
     WHERE table_name = 'USERS';

    IF v_count > 0 THEN
        SELECT COUNT(*)
          INTO v_count
          FROM user_ind_columns
         WHERE table_name = 'USERS'
           AND column_name = 'EMAIL'
           AND column_position = 1;

        IF v_count = 0 THEN
            EXECUTE IMMEDIATE 'CREATE INDEX idx_users_email_lookup ON users(email)';
        END IF;
    END IF;
END;
/
