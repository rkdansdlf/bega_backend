-- V72: Ensure legacy and underscored team columns both exist for parties.
-- Some environments were created with HOME_TEAM/AWAY_TEAM, while the Party entity maps hometeam/awayteam.

DECLARE
    v_column_count NUMBER;
    v_source_count NUMBER;
BEGIN
    SELECT COUNT(*)
      INTO v_column_count
      FROM user_tab_cols
     WHERE table_name = 'PARTIES'
       AND column_name = 'HOMETEAM';

    IF v_column_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE parties ADD (hometeam VARCHAR2(20))';

        SELECT COUNT(*)
          INTO v_source_count
          FROM user_tab_cols
         WHERE table_name = 'PARTIES'
           AND column_name = 'HOME_TEAM';

        IF v_source_count > 0 THEN
            EXECUTE IMMEDIATE 'UPDATE parties SET hometeam = home_team WHERE hometeam IS NULL AND home_team IS NOT NULL';
        END IF;
    END IF;

    SELECT COUNT(*)
      INTO v_column_count
      FROM user_tab_cols
     WHERE table_name = 'PARTIES'
       AND column_name = 'AWAYTEAM';

    IF v_column_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE parties ADD (awayteam VARCHAR2(20))';

        SELECT COUNT(*)
          INTO v_source_count
          FROM user_tab_cols
         WHERE table_name = 'PARTIES'
           AND column_name = 'AWAY_TEAM';

        IF v_source_count > 0 THEN
            EXECUTE IMMEDIATE 'UPDATE parties SET awayteam = away_team WHERE awayteam IS NULL AND away_team IS NOT NULL';
        END IF;
    END IF;
END;
/
