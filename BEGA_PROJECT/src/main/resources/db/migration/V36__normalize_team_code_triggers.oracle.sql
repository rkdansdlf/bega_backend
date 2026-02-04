-- Normalize legacy team codes on write and backfill remaining tables (Oracle)

-- Mapping:
-- KIA -> HT
-- KI  -> WO
-- NX  -> WO
-- DO  -> OB
-- BE  -> HH
-- SK  -> SSG
-- SL  -> SSG
-- MBC -> LG
-- LOT -> LT

DECLARE
    FUNCTION table_exists(p_table VARCHAR2) RETURN BOOLEAN IS
        v_count INTEGER;
    BEGIN
        SELECT COUNT(*) INTO v_count FROM user_tables WHERE table_name = UPPER(p_table);
        RETURN v_count > 0;
    END;
BEGIN
    IF table_exists('GAME_BATTING_STATS') THEN
        EXECUTE IMMEDIATE q'[
UPDATE /*+ NO_PARALLEL */ game_batting_stats
SET team_code = CASE team_code
    WHEN 'KIA' THEN 'HT'
    WHEN 'KI' THEN 'WO'
    WHEN 'NX' THEN 'WO'
    WHEN 'DO' THEN 'OB'
    WHEN 'BE' THEN 'HH'
    WHEN 'SK' THEN 'SSG'
    WHEN 'SL' THEN 'SSG'
    WHEN 'MBC' THEN 'LG'
    WHEN 'LOT' THEN 'LT'
    ELSE team_code
END
]';
    END IF;

    IF table_exists('GAME_PITCHING_STATS') THEN
        EXECUTE IMMEDIATE q'[
UPDATE /*+ NO_PARALLEL */ game_pitching_stats
SET team_code = CASE team_code
    WHEN 'KIA' THEN 'HT'
    WHEN 'KI' THEN 'WO'
    WHEN 'NX' THEN 'WO'
    WHEN 'DO' THEN 'OB'
    WHEN 'BE' THEN 'HH'
    WHEN 'SK' THEN 'SSG'
    WHEN 'SL' THEN 'SSG'
    WHEN 'MBC' THEN 'LG'
    WHEN 'LOT' THEN 'LT'
    ELSE team_code
END
]';
    END IF;

    IF table_exists('GAME_LINEUPS') THEN
        EXECUTE IMMEDIATE q'[
UPDATE /*+ NO_PARALLEL */ game_lineups
SET team_code = CASE team_code
    WHEN 'KIA' THEN 'HT'
    WHEN 'KI' THEN 'WO'
    WHEN 'NX' THEN 'WO'
    WHEN 'DO' THEN 'OB'
    WHEN 'BE' THEN 'HH'
    WHEN 'SK' THEN 'SSG'
    WHEN 'SL' THEN 'SSG'
    WHEN 'MBC' THEN 'LG'
    WHEN 'LOT' THEN 'LT'
    ELSE team_code
END
]';
    END IF;

    IF table_exists('GAME_INNING_SCORES') THEN
        EXECUTE IMMEDIATE q'[
UPDATE /*+ NO_PARALLEL */ game_inning_scores
SET team_code = CASE team_code
    WHEN 'KIA' THEN 'HT'
    WHEN 'KI' THEN 'WO'
    WHEN 'NX' THEN 'WO'
    WHEN 'DO' THEN 'OB'
    WHEN 'BE' THEN 'HH'
    WHEN 'SK' THEN 'SSG'
    WHEN 'SL' THEN 'SSG'
    WHEN 'MBC' THEN 'LG'
    WHEN 'LOT' THEN 'LT'
    ELSE team_code
END
]';
    END IF;

    IF table_exists('PLAYER_MOVEMENTS') THEN
        EXECUTE IMMEDIATE q'[
UPDATE /*+ NO_PARALLEL */ player_movements
SET team_code = CASE team_code
    WHEN 'KIA' THEN 'HT'
    WHEN 'KI' THEN 'WO'
    WHEN 'NX' THEN 'WO'
    WHEN 'DO' THEN 'OB'
    WHEN 'BE' THEN 'HH'
    WHEN 'SK' THEN 'SSG'
    WHEN 'SL' THEN 'SSG'
    WHEN 'MBC' THEN 'LG'
    WHEN 'LOT' THEN 'LT'
    ELSE team_code
END
]';
    END IF;

    IF table_exists('TEAM_DAILY_ROSTER') THEN
        EXECUTE IMMEDIATE q'[
UPDATE /*+ NO_PARALLEL */ team_daily_roster
SET team_code = CASE team_code
    WHEN 'KIA' THEN 'HT'
    WHEN 'KI' THEN 'WO'
    WHEN 'NX' THEN 'WO'
    WHEN 'DO' THEN 'OB'
    WHEN 'BE' THEN 'HH'
    WHEN 'SK' THEN 'SSG'
    WHEN 'SL' THEN 'SSG'
    WHEN 'MBC' THEN 'LG'
    WHEN 'LOT' THEN 'LT'
    ELSE team_code
END
]';
    END IF;

    IF table_exists('TEAM_SEASON_BATTING_SUMMARY') THEN
        EXECUTE IMMEDIATE q'[
UPDATE /*+ NO_PARALLEL */ team_season_batting_summary
SET team_id = CASE team_id
    WHEN 'KIA' THEN 'HT'
    WHEN 'KI' THEN 'WO'
    WHEN 'NX' THEN 'WO'
    WHEN 'DO' THEN 'OB'
    WHEN 'BE' THEN 'HH'
    WHEN 'SK' THEN 'SSG'
    WHEN 'SL' THEN 'SSG'
    WHEN 'MBC' THEN 'LG'
    WHEN 'LOT' THEN 'LT'
    ELSE team_id
END
]';
    END IF;

    IF table_exists('CHEER_BATTLE_VOTES') THEN
        EXECUTE IMMEDIATE q'[
UPDATE /*+ NO_PARALLEL */ cheer_battle_votes
SET team_id = CASE team_id
    WHEN 'KIA' THEN 'HT'
    WHEN 'KI' THEN 'WO'
    WHEN 'NX' THEN 'WO'
    WHEN 'DO' THEN 'OB'
    WHEN 'BE' THEN 'HH'
    WHEN 'SK' THEN 'SSG'
    WHEN 'SL' THEN 'SSG'
    WHEN 'MBC' THEN 'LG'
    WHEN 'LOT' THEN 'LT'
    ELSE team_id
END
]';
    END IF;
END;
/

-- Normalize function (Oracle PL/SQL)
CREATE OR REPLACE FUNCTION normalize_team_code(input_code VARCHAR2)
RETURN VARCHAR2
AS
BEGIN
    IF input_code IS NULL THEN
        RETURN NULL;
    END IF;

    CASE UPPER(TRIM(input_code))
        WHEN 'KIA' THEN RETURN 'HT';
        WHEN 'KI' THEN RETURN 'WO';
        WHEN 'NX' THEN RETURN 'WO';
        WHEN 'DO' THEN RETURN 'OB';
        WHEN 'BE' THEN RETURN 'HH';
        WHEN 'SK' THEN RETURN 'SSG';
        WHEN 'SL' THEN RETURN 'SSG';
        WHEN 'MBC' THEN RETURN 'LG';
        WHEN 'LOT' THEN RETURN 'LT';
        ELSE RETURN UPPER(TRIM(input_code));
    END CASE;
END;
/

DECLARE
    FUNCTION table_exists(p_table VARCHAR2) RETURN BOOLEAN IS
        v_count INTEGER;
    BEGIN
        SELECT COUNT(*) INTO v_count FROM user_tables WHERE table_name = UPPER(p_table);
        RETURN v_count > 0;
    END;
BEGIN
    IF table_exists('GAME') THEN
        EXECUTE IMMEDIATE q'[
CREATE OR REPLACE TRIGGER trg_normalize_game_team_codes
BEFORE INSERT OR UPDATE ON game
FOR EACH ROW
BEGIN
    :NEW.home_team := normalize_team_code(:NEW.home_team);
    :NEW.away_team := normalize_team_code(:NEW.away_team);
    :NEW.winning_team := normalize_team_code(:NEW.winning_team);
END;
]';
    END IF;

    IF table_exists('GAME_BATTING_STATS') THEN
        EXECUTE IMMEDIATE q'[
CREATE OR REPLACE TRIGGER trg_normalize_game_batting_stats_team_code
BEFORE INSERT OR UPDATE ON game_batting_stats
FOR EACH ROW
BEGIN
    :NEW.team_code := normalize_team_code(:NEW.team_code);
END;
]';
    END IF;

    IF table_exists('GAME_PITCHING_STATS') THEN
        EXECUTE IMMEDIATE q'[
CREATE OR REPLACE TRIGGER trg_normalize_game_pitching_stats_team_code
BEFORE INSERT OR UPDATE ON game_pitching_stats
FOR EACH ROW
BEGIN
    :NEW.team_code := normalize_team_code(:NEW.team_code);
END;
]';
    END IF;

    IF table_exists('GAME_LINEUPS') THEN
        EXECUTE IMMEDIATE q'[
CREATE OR REPLACE TRIGGER trg_normalize_game_lineups_team_code
BEFORE INSERT OR UPDATE ON game_lineups
FOR EACH ROW
BEGIN
    :NEW.team_code := normalize_team_code(:NEW.team_code);
END;
]';
    END IF;

    IF table_exists('GAME_INNING_SCORES') THEN
        EXECUTE IMMEDIATE q'[
CREATE OR REPLACE TRIGGER trg_normalize_game_inning_scores_team_code
BEFORE INSERT OR UPDATE ON game_inning_scores
FOR EACH ROW
BEGIN
    :NEW.team_code := normalize_team_code(:NEW.team_code);
END;
]';
    END IF;

    IF table_exists('PLAYER_MOVEMENTS') THEN
        EXECUTE IMMEDIATE q'[
CREATE OR REPLACE TRIGGER trg_normalize_player_movements_team_code
BEFORE INSERT OR UPDATE ON player_movements
FOR EACH ROW
BEGIN
    :NEW.team_code := normalize_team_code(:NEW.team_code);
END;
]';
    END IF;

    IF table_exists('TEAM_DAILY_ROSTER') THEN
        EXECUTE IMMEDIATE q'[
CREATE OR REPLACE TRIGGER trg_normalize_team_daily_roster_team_code
BEFORE INSERT OR UPDATE ON team_daily_roster
FOR EACH ROW
BEGIN
    :NEW.team_code := normalize_team_code(:NEW.team_code);
END;
]';
    END IF;

    IF table_exists('PLAYER_SEASON_BATTING') THEN
        EXECUTE IMMEDIATE q'[
CREATE OR REPLACE TRIGGER trg_normalize_player_season_batting_team_code
BEFORE INSERT OR UPDATE ON player_season_batting
FOR EACH ROW
BEGIN
    :NEW.team_code := normalize_team_code(:NEW.team_code);
END;
]';
    END IF;

    IF table_exists('PLAYER_SEASON_PITCHING') THEN
        EXECUTE IMMEDIATE q'[
CREATE OR REPLACE TRIGGER trg_normalize_player_season_pitching_team_code
BEFORE INSERT OR UPDATE ON player_season_pitching
FOR EACH ROW
BEGIN
    :NEW.team_code := normalize_team_code(:NEW.team_code);
END;
]';
    END IF;

    IF table_exists('TEAM_SEASON_BATTING_SUMMARY') THEN
        EXECUTE IMMEDIATE q'[
CREATE OR REPLACE TRIGGER trg_normalize_team_season_batting_summary_team_id
BEFORE INSERT OR UPDATE ON team_season_batting_summary
FOR EACH ROW
BEGIN
    :NEW.team_id := normalize_team_code(:NEW.team_id);
END;
]';
    END IF;

    IF table_exists('TEAM_PROFILES') THEN
        EXECUTE IMMEDIATE q'[
CREATE OR REPLACE TRIGGER trg_normalize_team_profiles_team_id
BEFORE INSERT OR UPDATE ON team_profiles
FOR EACH ROW
BEGIN
    :NEW.team_id := normalize_team_code(:NEW.team_id);
END;
]';
    END IF;

    IF table_exists('CHEER_POST') THEN
        EXECUTE IMMEDIATE q'[
CREATE OR REPLACE TRIGGER trg_normalize_cheer_post_team_id
BEFORE INSERT OR UPDATE ON cheer_post
FOR EACH ROW
BEGIN
    :NEW.team_id := normalize_team_code(:NEW.team_id);
END;
]';
    END IF;

    IF table_exists('CHEER_BATTLE_VOTES') THEN
        EXECUTE IMMEDIATE q'[
CREATE OR REPLACE TRIGGER trg_normalize_cheer_battle_votes_team_id
BEFORE INSERT OR UPDATE ON cheer_battle_votes
FOR EACH ROW
BEGIN
    :NEW.team_id := normalize_team_code(:NEW.team_id);
END;
]';
    END IF;

    IF table_exists('USERS') THEN
        EXECUTE IMMEDIATE q'[
CREATE OR REPLACE TRIGGER trg_normalize_users_favorite_team
BEFORE INSERT OR UPDATE ON users
FOR EACH ROW
BEGIN
    :NEW.favorite_team := normalize_team_code(:NEW.favorite_team);
END;
]';
    END IF;
END;
/
