-- Normalize team codes to latest canonical policy.
-- Canonical set: SS, LT, LG, DB, KIA, KH, HH, SSG, NC, KT
--
-- Legacy/old mappings:
-- HT -> KIA
-- DO -> DB
-- OB -> DB
-- KI -> KH
-- NX -> KH
-- WO -> KH
-- KW -> KH
-- SK -> SSG
-- SL -> SSG
-- BE -> HH
-- MBC -> LG
-- LOT -> LT

CREATE OR REPLACE FUNCTION normalize_team_code(p_code VARCHAR2)
RETURN VARCHAR2
IS
    v_input VARCHAR2(10) := UPPER(TRIM(p_code));
    v_mapped VARCHAR2(10);
    v_parent_exists INTEGER;
    v_team_table_exists INTEGER;
BEGIN
    IF v_input IS NULL THEN
        RETURN NULL;
    END IF;

    v_mapped := CASE v_input
        WHEN 'HT' THEN 'KIA'
        WHEN 'DO' THEN 'DB'
        WHEN 'OB' THEN 'DB'
        WHEN 'KI' THEN 'KH'
        WHEN 'NX' THEN 'KH'
        WHEN 'WO' THEN 'KH'
        WHEN 'KW' THEN 'KH'
        WHEN 'BE' THEN 'HH'
        WHEN 'SK' THEN 'SSG'
        WHEN 'SL' THEN 'SSG'
        WHEN 'MBC' THEN 'LG'
        WHEN 'LOT' THEN 'LT'
        ELSE v_input
    END;

    IF v_mapped = v_input THEN
        RETURN v_input;
    END IF;

    SELECT COUNT(*) INTO v_team_table_exists
    FROM user_tables
    WHERE table_name = 'TEAMS';
    IF v_team_table_exists = 0 THEN
        RETURN v_input;
    END IF;

    SELECT COUNT(*) INTO v_parent_exists
    FROM teams
    WHERE team_id = v_mapped;
    IF v_parent_exists = 0 THEN
        RETURN v_input;
    END IF;

    RETURN v_mapped;
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
UPDATE game
SET home_team = normalize_team_code(home_team)
WHERE home_team IN ('HT', 'DO', 'OB', 'KI', 'NX', 'WO', 'KW', 'SK', 'SL')
]';
        EXECUTE IMMEDIATE q'[
UPDATE game
SET away_team = normalize_team_code(away_team)
WHERE away_team IN ('HT', 'DO', 'OB', 'KI', 'NX', 'WO', 'KW', 'SK', 'SL')
]';
        EXECUTE IMMEDIATE q'[
UPDATE game
SET winning_team = normalize_team_code(winning_team)
WHERE winning_team IN ('HT', 'DO', 'OB', 'KI', 'NX', 'WO', 'KW', 'SK', 'SL')
]';
    END IF;

    IF table_exists('GAME_BATTING_STATS') THEN
        EXECUTE IMMEDIATE q'[
UPDATE game_batting_stats
SET team_code = normalize_team_code(team_code)
WHERE team_code IN ('HT', 'DO', 'OB', 'KI', 'NX', 'WO', 'KW', 'SK', 'SL')
]';
    END IF;

    IF table_exists('GAME_PITCHING_STATS') THEN
        EXECUTE IMMEDIATE q'[
UPDATE game_pitching_stats
SET team_code = normalize_team_code(team_code)
WHERE team_code IN ('HT', 'DO', 'OB', 'KI', 'NX', 'WO', 'KW', 'SK', 'SL')
]';
    END IF;

    IF table_exists('GAME_LINEUPS') THEN
        EXECUTE IMMEDIATE q'[
UPDATE game_lineups
SET team_code = normalize_team_code(team_code)
WHERE team_code IN ('HT', 'DO', 'OB', 'KI', 'NX', 'WO', 'KW', 'SK', 'SL')
]';
    END IF;

    IF table_exists('GAME_INNING_SCORES') THEN
        EXECUTE IMMEDIATE q'[
UPDATE game_inning_scores
SET team_code = normalize_team_code(team_code)
WHERE team_code IN ('HT', 'DO', 'OB', 'KI', 'NX', 'WO', 'KW', 'SK', 'SL')
]';
    END IF;

    IF table_exists('PLAYER_MOVEMENTS') THEN
        EXECUTE IMMEDIATE q'[
UPDATE player_movements
SET team_code = normalize_team_code(team_code)
WHERE team_code IN ('HT', 'DO', 'OB', 'KI', 'NX', 'WO', 'KW', 'SK', 'SL')
]';
    END IF;

    IF table_exists('TEAM_DAILY_ROSTER') THEN
        EXECUTE IMMEDIATE q'[
UPDATE team_daily_roster
SET team_code = normalize_team_code(team_code)
WHERE team_code IN ('HT', 'DO', 'OB', 'KI', 'NX', 'WO', 'KW', 'SK', 'SL')
]';
    END IF;

    IF table_exists('TEAM_SEASON_BATTING_SUMMARY') THEN
        EXECUTE IMMEDIATE q'[
UPDATE team_season_batting_summary
SET team_id = normalize_team_code(team_id)
WHERE team_id IN ('HT', 'DO', 'OB', 'KI', 'NX', 'WO', 'KW', 'SK', 'SL')
]';
    END IF;

    IF table_exists('PLAYER_SEASON_BATTING') THEN
        EXECUTE IMMEDIATE q'[
UPDATE player_season_batting
SET team_code = normalize_team_code(team_code)
WHERE team_code IN ('HT', 'DO', 'OB', 'KI', 'NX', 'WO', 'KW', 'SK', 'SL')
]';
    END IF;

    IF table_exists('PLAYER_SEASON_PITCHING') THEN
        EXECUTE IMMEDIATE q'[
UPDATE player_season_pitching
SET team_code = normalize_team_code(team_code)
WHERE team_code IN ('HT', 'DO', 'OB', 'KI', 'NX', 'WO', 'KW', 'SK', 'SL')
]';
    END IF;

    IF table_exists('CHEER_POST') THEN
        EXECUTE IMMEDIATE q'[
UPDATE cheer_post
SET team_id = normalize_team_code(team_id)
WHERE team_id IN ('HT', 'DO', 'OB', 'KI', 'NX', 'WO', 'KW', 'SK', 'SL')
]';
    END IF;

    IF table_exists('TEAM_PROFILES') THEN
        EXECUTE IMMEDIATE q'[
UPDATE team_profiles
SET team_id = normalize_team_code(team_id)
WHERE team_id IN ('HT', 'DO', 'OB', 'KI', 'NX', 'WO', 'KW', 'SK', 'SL')
]';
    END IF;

    IF table_exists('CHEER_BATTLE_VOTES') THEN
        EXECUTE IMMEDIATE q'[
UPDATE cheer_battle_votes
SET team_id = normalize_team_code(team_id)
WHERE team_id IN ('HT', 'DO', 'OB', 'KI', 'NX', 'WO', 'KW', 'SK', 'SL')
]';
    END IF;

    IF table_exists('USERS') THEN
        EXECUTE IMMEDIATE q'[
UPDATE users
SET favorite_team = normalize_team_code(favorite_team)
WHERE favorite_team IN ('HT', 'DO', 'OB', 'KI', 'NX', 'WO', 'KW', 'SK', 'SL')
]';
    END IF;
END;
/
