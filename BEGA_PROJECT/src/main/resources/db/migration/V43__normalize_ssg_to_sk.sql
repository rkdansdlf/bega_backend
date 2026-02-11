-- Standardize SSG legacy code to SK canonical code.
-- Existing triggers call normalize_team_code(), so redefining this function
-- flips write-time normalization without recreating triggers.

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
SET home_team = CASE home_team
    WHEN 'SSG' THEN 'SK'
    WHEN 'SL' THEN 'SK'
    ELSE home_team
END
WHERE home_team IN ('SSG', 'SL')
]';
        EXECUTE IMMEDIATE q'[
UPDATE game
SET away_team = CASE away_team
    WHEN 'SSG' THEN 'SK'
    WHEN 'SL' THEN 'SK'
    ELSE away_team
END
WHERE away_team IN ('SSG', 'SL')
]';
        EXECUTE IMMEDIATE q'[
UPDATE game
SET winning_team = CASE winning_team
    WHEN 'SSG' THEN 'SK'
    WHEN 'SL' THEN 'SK'
    ELSE winning_team
END
WHERE winning_team IN ('SSG', 'SL')
]';
    END IF;

    IF table_exists('GAME_BATTING_STATS') THEN
        EXECUTE IMMEDIATE q'[
UPDATE game_batting_stats
SET team_code = CASE team_code
    WHEN 'SSG' THEN 'SK'
    WHEN 'SL' THEN 'SK'
    ELSE team_code
END
WHERE team_code IN ('SSG', 'SL')
]';
    END IF;

    IF table_exists('GAME_PITCHING_STATS') THEN
        EXECUTE IMMEDIATE q'[
UPDATE game_pitching_stats
SET team_code = CASE team_code
    WHEN 'SSG' THEN 'SK'
    WHEN 'SL' THEN 'SK'
    ELSE team_code
END
WHERE team_code IN ('SSG', 'SL')
]';
    END IF;

    IF table_exists('GAME_LINEUPS') THEN
        EXECUTE IMMEDIATE q'[
UPDATE game_lineups
SET team_code = CASE team_code
    WHEN 'SSG' THEN 'SK'
    WHEN 'SL' THEN 'SK'
    ELSE team_code
END
WHERE team_code IN ('SSG', 'SL')
]';
    END IF;

    IF table_exists('GAME_INNING_SCORES') THEN
        EXECUTE IMMEDIATE q'[
UPDATE game_inning_scores
SET team_code = CASE team_code
    WHEN 'SSG' THEN 'SK'
    WHEN 'SL' THEN 'SK'
    ELSE team_code
END
WHERE team_code IN ('SSG', 'SL')
]';
    END IF;

    IF table_exists('PLAYER_MOVEMENTS') THEN
        EXECUTE IMMEDIATE q'[
UPDATE player_movements
SET team_code = CASE team_code
    WHEN 'SSG' THEN 'SK'
    WHEN 'SL' THEN 'SK'
    ELSE team_code
END
WHERE team_code IN ('SSG', 'SL')
]';
    END IF;

    IF table_exists('TEAM_DAILY_ROSTER') THEN
        EXECUTE IMMEDIATE q'[
UPDATE team_daily_roster
SET team_code = CASE team_code
    WHEN 'SSG' THEN 'SK'
    WHEN 'SL' THEN 'SK'
    ELSE team_code
END
WHERE team_code IN ('SSG', 'SL')
]';
    END IF;

    IF table_exists('TEAM_SEASON_BATTING_SUMMARY') THEN
        EXECUTE IMMEDIATE q'[
UPDATE team_season_batting_summary
SET team_id = CASE team_id
    WHEN 'SSG' THEN 'SK'
    WHEN 'SL' THEN 'SK'
    ELSE team_id
END
WHERE team_id IN ('SSG', 'SL')
]';
    END IF;

    IF table_exists('PLAYER_SEASON_BATTING') THEN
        EXECUTE IMMEDIATE q'[
UPDATE player_season_batting
SET team_code = CASE team_code
    WHEN 'SSG' THEN 'SK'
    WHEN 'SL' THEN 'SK'
    ELSE team_code
END
WHERE team_code IN ('SSG', 'SL')
]';
    END IF;

    IF table_exists('PLAYER_SEASON_PITCHING') THEN
        EXECUTE IMMEDIATE q'[
UPDATE player_season_pitching
SET team_code = CASE team_code
    WHEN 'SSG' THEN 'SK'
    WHEN 'SL' THEN 'SK'
    ELSE team_code
END
WHERE team_code IN ('SSG', 'SL')
]';
    END IF;

    IF table_exists('CHEER_POST') THEN
        EXECUTE IMMEDIATE q'[
UPDATE cheer_post
SET team_id = CASE team_id
    WHEN 'SSG' THEN 'SK'
    WHEN 'SL' THEN 'SK'
    ELSE team_id
END
WHERE team_id IN ('SSG', 'SL')
]';
    END IF;

    IF table_exists('TEAM_PROFILES') THEN
        EXECUTE IMMEDIATE q'[
UPDATE team_profiles
SET team_id = CASE team_id
    WHEN 'SSG' THEN 'SK'
    WHEN 'SL' THEN 'SK'
    ELSE team_id
END
WHERE team_id IN ('SSG', 'SL')
]';
    END IF;

    IF table_exists('CHEER_BATTLE_VOTES') THEN
        EXECUTE IMMEDIATE q'[
UPDATE cheer_battle_votes
SET team_id = CASE team_id
    WHEN 'SSG' THEN 'SK'
    WHEN 'SL' THEN 'SK'
    ELSE team_id
END
WHERE team_id IN ('SSG', 'SL')
]';
    END IF;

    IF table_exists('USERS') THEN
        EXECUTE IMMEDIATE q'[
UPDATE users
SET favorite_team = CASE favorite_team
    WHEN 'SSG' THEN 'SK'
    WHEN 'SL' THEN 'SK'
    ELSE favorite_team
END
WHERE favorite_team IN ('SSG', 'SL')
]';
    END IF;
END;
/

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
        WHEN 'SSG' THEN RETURN 'SK';
        WHEN 'SL' THEN RETURN 'SK';
        WHEN 'MBC' THEN RETURN 'LG';
        WHEN 'LOT' THEN RETURN 'LT';
        ELSE RETURN UPPER(TRIM(input_code));
    END CASE;
END;
/
