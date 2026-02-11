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
    ELSE home_team
END
WHERE home_team IN ('HT', 'DO', 'OB', 'KI', 'NX', 'WO', 'KW', 'SK', 'SL');

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
    ELSE away_team
END
WHERE away_team IN ('HT', 'DO', 'OB', 'KI', 'NX', 'WO', 'KW', 'SK', 'SL');

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
    ELSE winning_team
END
WHERE winning_team IN ('HT', 'DO', 'OB', 'KI', 'NX', 'WO', 'KW', 'SK', 'SL');

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
    ELSE team_code
END
WHERE team_code IN ('HT', 'DO', 'OB', 'KI', 'NX', 'WO', 'KW', 'SK', 'SL');

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
    ELSE team_code
END
WHERE team_code IN ('HT', 'DO', 'OB', 'KI', 'NX', 'WO', 'KW', 'SK', 'SL');

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
    ELSE team_code
END
WHERE team_code IN ('HT', 'DO', 'OB', 'KI', 'NX', 'WO', 'KW', 'SK', 'SL');

UPDATE game_inning_scores
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
    ELSE team_code
END
WHERE team_code IN ('HT', 'DO', 'OB', 'KI', 'NX', 'WO', 'KW', 'SK', 'SL');

UPDATE player_movements
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
    ELSE team_code
END
WHERE team_code IN ('HT', 'DO', 'OB', 'KI', 'NX', 'WO', 'KW', 'SK', 'SL');

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
    ELSE team_code
END
WHERE team_code IN ('HT', 'DO', 'OB', 'KI', 'NX', 'WO', 'KW', 'SK', 'SL');

UPDATE team_season_batting_summary
SET team_id = CASE team_id
    WHEN 'HT' THEN 'KIA'
    WHEN 'DO' THEN 'DB'
    WHEN 'OB' THEN 'DB'
    WHEN 'KI' THEN 'KH'
    WHEN 'NX' THEN 'KH'
    WHEN 'WO' THEN 'KH'
    WHEN 'KW' THEN 'KH'
    WHEN 'SK' THEN 'SSG'
    WHEN 'SL' THEN 'SSG'
    ELSE team_id
END
WHERE team_id IN ('HT', 'DO', 'OB', 'KI', 'NX', 'WO', 'KW', 'SK', 'SL');

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
    ELSE team_code
END
WHERE team_code IN ('HT', 'DO', 'OB', 'KI', 'NX', 'WO', 'KW', 'SK', 'SL');

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
    ELSE team_code
END
WHERE team_code IN ('HT', 'DO', 'OB', 'KI', 'NX', 'WO', 'KW', 'SK', 'SL');

UPDATE cheer_post
SET team_id = CASE team_id
    WHEN 'HT' THEN 'KIA'
    WHEN 'DO' THEN 'DB'
    WHEN 'OB' THEN 'DB'
    WHEN 'KI' THEN 'KH'
    WHEN 'NX' THEN 'KH'
    WHEN 'WO' THEN 'KH'
    WHEN 'KW' THEN 'KH'
    WHEN 'SK' THEN 'SSG'
    WHEN 'SL' THEN 'SSG'
    ELSE team_id
END
WHERE team_id IN ('HT', 'DO', 'OB', 'KI', 'NX', 'WO', 'KW', 'SK', 'SL');

UPDATE team_profiles
SET team_id = CASE team_id
    WHEN 'HT' THEN 'KIA'
    WHEN 'DO' THEN 'DB'
    WHEN 'OB' THEN 'DB'
    WHEN 'KI' THEN 'KH'
    WHEN 'NX' THEN 'KH'
    WHEN 'WO' THEN 'KH'
    WHEN 'KW' THEN 'KH'
    WHEN 'SK' THEN 'SSG'
    WHEN 'SL' THEN 'SSG'
    ELSE team_id
END
WHERE team_id IN ('HT', 'DO', 'OB', 'KI', 'NX', 'WO', 'KW', 'SK', 'SL');

UPDATE cheer_battle_votes
SET team_id = CASE team_id
    WHEN 'HT' THEN 'KIA'
    WHEN 'DO' THEN 'DB'
    WHEN 'OB' THEN 'DB'
    WHEN 'KI' THEN 'KH'
    WHEN 'NX' THEN 'KH'
    WHEN 'WO' THEN 'KH'
    WHEN 'KW' THEN 'KH'
    WHEN 'SK' THEN 'SSG'
    WHEN 'SL' THEN 'SSG'
    ELSE team_id
END
WHERE team_id IN ('HT', 'DO', 'OB', 'KI', 'NX', 'WO', 'KW', 'SK', 'SL');

UPDATE users
SET favorite_team = CASE favorite_team
    WHEN 'HT' THEN 'KIA'
    WHEN 'DO' THEN 'DB'
    WHEN 'OB' THEN 'DB'
    WHEN 'KI' THEN 'KH'
    WHEN 'NX' THEN 'KH'
    WHEN 'WO' THEN 'KH'
    WHEN 'KW' THEN 'KH'
    WHEN 'SK' THEN 'SSG'
    WHEN 'SL' THEN 'SSG'
    ELSE favorite_team
END
WHERE favorite_team IN ('HT', 'DO', 'OB', 'KI', 'NX', 'WO', 'KW', 'SK', 'SL');

CREATE OR REPLACE FUNCTION normalize_team_code(input_code text)
RETURNS text AS $$
BEGIN
    IF input_code IS NULL THEN
        RETURN NULL;
    END IF;

    CASE UPPER(BTRIM(input_code))
        WHEN 'HT' THEN RETURN 'KIA';
        WHEN 'DO' THEN RETURN 'DB';
        WHEN 'OB' THEN RETURN 'DB';
        WHEN 'KI' THEN RETURN 'KH';
        WHEN 'NX' THEN RETURN 'KH';
        WHEN 'WO' THEN RETURN 'KH';
        WHEN 'KW' THEN RETURN 'KH';
        WHEN 'BE' THEN RETURN 'HH';
        WHEN 'SK' THEN RETURN 'SSG';
        WHEN 'SL' THEN RETURN 'SSG';
        WHEN 'MBC' THEN RETURN 'LG';
        WHEN 'LOT' THEN RETURN 'LT';
        ELSE RETURN UPPER(BTRIM(input_code));
    END CASE;
END;
$$ LANGUAGE plpgsql;
