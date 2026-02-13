-- Canonicalize baseball team codes for dual-read -> canonical-only transition.
-- Canonical set: SS, LT, LG, DB, KIA, KH, HH, SSG, NC, KT

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
    WHEN 'HT' THEN 'KIA'
    WHEN 'DO' THEN 'DB'
    WHEN 'OB' THEN 'DB'
    WHEN 'KI' THEN 'KH'
    WHEN 'NX' THEN 'KH'
    WHEN 'WO' THEN 'KH'
    WHEN 'KW' THEN 'KH'
    WHEN 'SK' THEN 'SSG'
    WHEN 'SL' THEN 'SSG'
    WHEN 'BE' THEN 'HH'
    WHEN 'MBC' THEN 'LG'
    WHEN 'LOT' THEN 'LT'
    ELSE home_team
END
WHERE home_team IN ('HT', 'DO', 'OB', 'KI', 'NX', 'WO', 'KW', 'SK', 'SL', 'BE', 'MBC', 'LOT')
]';
        EXECUTE IMMEDIATE q'[
UPDATE game
SET away_team = CASE away_team
    WHEN 'HT' THEN 'KIA'
    WHEN 'DO' THEN 'DB'
    WHEN 'OB' THEN 'DB'
    WHEN 'KI' THEN 'KH'
    WHEN 'NX' THEN 'KH'
    WHEN 'WO' THEN 'KH'
    WHEN 'KW' THEN 'KH'
    WHEN 'SK' THEN 'SSG'
    WHEN 'SL' THEN 'SSG'
    WHEN 'BE' THEN 'HH'
    WHEN 'MBC' THEN 'LG'
    WHEN 'LOT' THEN 'LT'
    ELSE away_team
END
WHERE away_team IN ('HT', 'DO', 'OB', 'KI', 'NX', 'WO', 'KW', 'SK', 'SL', 'BE', 'MBC', 'LOT')
]';
        EXECUTE IMMEDIATE q'[
UPDATE game
SET winning_team = CASE winning_team
    WHEN 'HT' THEN 'KIA'
    WHEN 'DO' THEN 'DB'
    WHEN 'OB' THEN 'DB'
    WHEN 'KI' THEN 'KH'
    WHEN 'NX' THEN 'KH'
    WHEN 'WO' THEN 'KH'
    WHEN 'KW' THEN 'KH'
    WHEN 'SK' THEN 'SSG'
    WHEN 'SL' THEN 'SSG'
    WHEN 'BE' THEN 'HH'
    WHEN 'MBC' THEN 'LG'
    WHEN 'LOT' THEN 'LT'
    ELSE winning_team
END
WHERE winning_team IN ('HT', 'DO', 'OB', 'KI', 'NX', 'WO', 'KW', 'SK', 'SL', 'BE', 'MBC', 'LOT')
]';
    END IF;

    IF table_exists('PLAYER_SEASON_BATTING') THEN
        EXECUTE IMMEDIATE q'[
UPDATE player_season_batting
SET team_code = CASE team_code
    WHEN 'HT' THEN 'KIA'
    WHEN 'DO' THEN 'DB'
    WHEN 'OB' THEN 'DB'
    WHEN 'KI' THEN 'KH'
    WHEN 'NX' THEN 'KH'
    WHEN 'WO' THEN 'KH'
    WHEN 'KW' THEN 'KH'
    WHEN 'SK' THEN 'SSG'
    WHEN 'SL' THEN 'SSG'
    WHEN 'BE' THEN 'HH'
    WHEN 'MBC' THEN 'LG'
    WHEN 'LOT' THEN 'LT'
    ELSE team_code
END
WHERE team_code IN ('HT', 'DO', 'OB', 'KI', 'NX', 'WO', 'KW', 'SK', 'SL', 'BE', 'MBC', 'LOT')
]';
    END IF;

    IF table_exists('PLAYER_SEASON_PITCHING') THEN
        EXECUTE IMMEDIATE q'[
UPDATE player_season_pitching
SET team_code = CASE team_code
    WHEN 'HT' THEN 'KIA'
    WHEN 'DO' THEN 'DB'
    WHEN 'OB' THEN 'DB'
    WHEN 'KI' THEN 'KH'
    WHEN 'NX' THEN 'KH'
    WHEN 'WO' THEN 'KH'
    WHEN 'KW' THEN 'KH'
    WHEN 'SK' THEN 'SSG'
    WHEN 'SL' THEN 'SSG'
    WHEN 'BE' THEN 'HH'
    WHEN 'MBC' THEN 'LG'
    WHEN 'LOT' THEN 'LT'
    ELSE team_code
END
WHERE team_code IN ('HT', 'DO', 'OB', 'KI', 'NX', 'WO', 'KW', 'SK', 'SL', 'BE', 'MBC', 'LOT')
]';
    END IF;

    IF table_exists('GAME_LINEUPS') THEN
        EXECUTE IMMEDIATE q'[
UPDATE game_lineups
SET team_code = CASE team_code
    WHEN 'HT' THEN 'KIA'
    WHEN 'DO' THEN 'DB'
    WHEN 'OB' THEN 'DB'
    WHEN 'KI' THEN 'KH'
    WHEN 'NX' THEN 'KH'
    WHEN 'WO' THEN 'KH'
    WHEN 'KW' THEN 'KH'
    WHEN 'SK' THEN 'SSG'
    WHEN 'SL' THEN 'SSG'
    WHEN 'BE' THEN 'HH'
    WHEN 'MBC' THEN 'LG'
    WHEN 'LOT' THEN 'LT'
    ELSE team_code
END
WHERE team_code IN ('HT', 'DO', 'OB', 'KI', 'NX', 'WO', 'KW', 'SK', 'SL', 'BE', 'MBC', 'LOT')
]';
    END IF;

    IF table_exists('GAME_BATTING_STATS') THEN
        EXECUTE IMMEDIATE q'[
UPDATE game_batting_stats
SET team_code = CASE team_code
    WHEN 'HT' THEN 'KIA'
    WHEN 'DO' THEN 'DB'
    WHEN 'OB' THEN 'DB'
    WHEN 'KI' THEN 'KH'
    WHEN 'NX' THEN 'KH'
    WHEN 'WO' THEN 'KH'
    WHEN 'KW' THEN 'KH'
    WHEN 'SK' THEN 'SSG'
    WHEN 'SL' THEN 'SSG'
    WHEN 'BE' THEN 'HH'
    WHEN 'MBC' THEN 'LG'
    WHEN 'LOT' THEN 'LT'
    ELSE team_code
END
WHERE team_code IN ('HT', 'DO', 'OB', 'KI', 'NX', 'WO', 'KW', 'SK', 'SL', 'BE', 'MBC', 'LOT')
]';
    END IF;

    IF table_exists('GAME_PITCHING_STATS') THEN
        EXECUTE IMMEDIATE q'[
UPDATE game_pitching_stats
SET team_code = CASE team_code
    WHEN 'HT' THEN 'KIA'
    WHEN 'DO' THEN 'DB'
    WHEN 'OB' THEN 'DB'
    WHEN 'KI' THEN 'KH'
    WHEN 'NX' THEN 'KH'
    WHEN 'WO' THEN 'KH'
    WHEN 'KW' THEN 'KH'
    WHEN 'SK' THEN 'SSG'
    WHEN 'SL' THEN 'SSG'
    WHEN 'BE' THEN 'HH'
    WHEN 'MBC' THEN 'LG'
    WHEN 'LOT' THEN 'LT'
    ELSE team_code
END
WHERE team_code IN ('HT', 'DO', 'OB', 'KI', 'NX', 'WO', 'KW', 'SK', 'SL', 'BE', 'MBC', 'LOT')
]';
    END IF;

    IF table_exists('TEAM_DAILY_ROSTER') THEN
        EXECUTE IMMEDIATE q'[
UPDATE team_daily_roster
SET team_code = CASE team_code
    WHEN 'HT' THEN 'KIA'
    WHEN 'DO' THEN 'DB'
    WHEN 'OB' THEN 'DB'
    WHEN 'KI' THEN 'KH'
    WHEN 'NX' THEN 'KH'
    WHEN 'WO' THEN 'KH'
    WHEN 'KW' THEN 'KH'
    WHEN 'SK' THEN 'SSG'
    WHEN 'SL' THEN 'SSG'
    WHEN 'BE' THEN 'HH'
    WHEN 'MBC' THEN 'LG'
    WHEN 'LOT' THEN 'LT'
    ELSE team_code
END
WHERE team_code IN ('HT', 'DO', 'OB', 'KI', 'NX', 'WO', 'KW', 'SK', 'SL', 'BE', 'MBC', 'LOT')
]';
    END IF;
END;
/
