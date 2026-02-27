-- V81: Ensure parties.teamid exists for Party.teamId mapping.
-- Older environments may store team reference in TEAM_ID or not store any team column.

DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*)
      INTO v_count
      FROM user_tab_cols
     WHERE table_name = 'PARTIES'
       AND column_name = 'TEAMID';

    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE parties ADD (teamid VARCHAR2(20))';

        SELECT COUNT(*)
          INTO v_count
          FROM user_tab_cols
         WHERE table_name = 'PARTIES'
           AND column_name = 'TEAM_ID';

        IF v_count > 0 THEN
            EXECUTE IMMEDIATE 'UPDATE parties SET teamid = TEAM_ID WHERE teamid IS NULL AND TEAM_ID IS NOT NULL';
        END IF;

        SELECT COUNT(*)
          INTO v_count
          FROM user_tab_cols
         WHERE table_name = 'PARTIES'
           AND column_name = 'HOMETEAM';

        IF v_count > 0 THEN
            EXECUTE IMMEDIATE 'UPDATE parties SET teamid = hometeam WHERE teamid IS NULL AND hometeam IS NOT NULL';
        END IF;

        SELECT COUNT(*)
          INTO v_count
          FROM user_tab_cols
         WHERE table_name = 'PARTIES'
           AND column_name = 'HOME_TEAM';

        IF v_count > 0 THEN
            EXECUTE IMMEDIATE 'UPDATE parties SET teamid = home_team WHERE teamid IS NULL AND home_team IS NOT NULL';
        END IF;

        SELECT COUNT(*)
          INTO v_count
          FROM user_tab_cols
         WHERE table_name = 'PARTIES'
           AND column_name = 'AWAYTEAM';

        IF v_count > 0 THEN
            EXECUTE IMMEDIATE 'UPDATE parties SET teamid = awayteam WHERE teamid IS NULL AND teamid IS NULL AND awayteam IS NOT NULL';
        END IF;

        SELECT COUNT(*)
          INTO v_count
          FROM user_tab_cols
         WHERE table_name = 'PARTIES'
           AND column_name = 'AWAY_TEAM';

        IF v_count > 0 THEN
            EXECUTE IMMEDIATE 'UPDATE parties SET teamid = away_team WHERE teamid IS NULL AND away_team IS NOT NULL';
        END IF;

        EXECUTE IMMEDIATE q'[UPDATE parties SET teamid = ''UNKNOWN'' WHERE teamid IS NULL]';

        EXECUTE IMMEDIATE 'ALTER TABLE parties MODIFY (teamid VARCHAR2(20) NOT NULL)';
        EXECUTE IMMEDIATE 'ALTER TABLE parties MODIFY (teamid DEFAULT ''UNKNOWN'')';
    END IF;
END;
/
