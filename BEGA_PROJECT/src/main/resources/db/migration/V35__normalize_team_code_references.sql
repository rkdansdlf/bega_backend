-- Normalize legacy team codes in referenced tables

-- Mapping:
-- KIA -> HT
-- KI  -> WO
-- NX  -> WO
-- DO  -> OB
-- BE  -> HH
-- SK  -> SSG
-- SL  -> SSG
-- MBC -> LG

-- game table
UPDATE /*+ NO_PARALLEL */ game
SET home_team = CASE home_team
    WHEN 'KIA' THEN 'HT'
    WHEN 'KI' THEN 'WO'
    WHEN 'NX' THEN 'WO'
    WHEN 'DO' THEN 'OB'
    WHEN 'BE' THEN 'HH'
    WHEN 'SK' THEN 'SSG'
    WHEN 'SL' THEN 'SSG'
    WHEN 'MBC' THEN 'LG'
    ELSE home_team
END;

UPDATE /*+ NO_PARALLEL */ game
SET away_team = CASE away_team
    WHEN 'KIA' THEN 'HT'
    WHEN 'KI' THEN 'WO'
    WHEN 'NX' THEN 'WO'
    WHEN 'DO' THEN 'OB'
    WHEN 'BE' THEN 'HH'
    WHEN 'SK' THEN 'SSG'
    WHEN 'SL' THEN 'SSG'
    WHEN 'MBC' THEN 'LG'
    ELSE away_team
END;

UPDATE /*+ NO_PARALLEL */ game
SET winning_team = CASE winning_team
    WHEN 'KIA' THEN 'HT'
    WHEN 'KI' THEN 'WO'
    WHEN 'NX' THEN 'WO'
    WHEN 'DO' THEN 'OB'
    WHEN 'BE' THEN 'HH'
    WHEN 'SK' THEN 'SSG'
    WHEN 'SL' THEN 'SSG'
    WHEN 'MBC' THEN 'LG'
    ELSE winning_team
END
WHERE winning_team IS NOT NULL;

-- cheer_post
UPDATE /*+ NO_PARALLEL */ cheer_post
SET team_id = CASE team_id
    WHEN 'KIA' THEN 'HT'
    WHEN 'KI' THEN 'WO'
    WHEN 'NX' THEN 'WO'
    WHEN 'DO' THEN 'OB'
    WHEN 'BE' THEN 'HH'
    WHEN 'SK' THEN 'SSG'
    WHEN 'SL' THEN 'SSG'
    WHEN 'MBC' THEN 'LG'
    ELSE team_id
END;

-- team_profiles
UPDATE /*+ NO_PARALLEL */ team_profiles
SET team_id = CASE team_id
    WHEN 'KIA' THEN 'HT'
    WHEN 'KI' THEN 'WO'
    WHEN 'NX' THEN 'WO'
    WHEN 'DO' THEN 'OB'
    WHEN 'BE' THEN 'HH'
    WHEN 'SK' THEN 'SSG'
    WHEN 'SL' THEN 'SSG'
    WHEN 'MBC' THEN 'LG'
    ELSE team_id
END;

-- users
UPDATE /*+ NO_PARALLEL */ users
SET favorite_team = CASE favorite_team
    WHEN 'KIA' THEN 'HT'
    WHEN 'KI' THEN 'WO'
    WHEN 'NX' THEN 'WO'
    WHEN 'DO' THEN 'OB'
    WHEN 'BE' THEN 'HH'
    WHEN 'SK' THEN 'SSG'
    WHEN 'SL' THEN 'SSG'
    WHEN 'MBC' THEN 'LG'
    ELSE favorite_team
END
WHERE favorite_team IS NOT NULL;

-- player_season_batting
UPDATE /*+ NO_PARALLEL */ player_season_batting
SET team_code = CASE team_code
    WHEN 'KIA' THEN 'HT'
    WHEN 'KI' THEN 'WO'
    WHEN 'NX' THEN 'WO'
    WHEN 'DO' THEN 'OB'
    WHEN 'BE' THEN 'HH'
    WHEN 'SK' THEN 'SSG'
    WHEN 'SL' THEN 'SSG'
    WHEN 'MBC' THEN 'LG'
    ELSE team_code
END;

-- player_season_pitching
UPDATE /*+ NO_PARALLEL */ player_season_pitching
SET team_code = CASE team_code
    WHEN 'KIA' THEN 'HT'
    WHEN 'KI' THEN 'WO'
    WHEN 'NX' THEN 'WO'
    WHEN 'DO' THEN 'OB'
    WHEN 'BE' THEN 'HH'
    WHEN 'SK' THEN 'SSG'
    WHEN 'SL' THEN 'SSG'
    WHEN 'MBC' THEN 'LG'
    ELSE team_code
END;
