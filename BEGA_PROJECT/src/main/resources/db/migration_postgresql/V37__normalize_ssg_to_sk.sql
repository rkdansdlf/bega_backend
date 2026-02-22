-- Standardize SSG legacy code to SK canonical code.
-- Existing triggers call normalize_team_code(), so redefining this function
-- flips write-time normalization without recreating triggers.

-- Backfill previously normalized rows (SSG/SL -> SK)
UPDATE game
SET home_team = CASE home_team
    WHEN 'SSG' THEN 'SK'
    WHEN 'SL' THEN 'SK'
    ELSE home_team
END
WHERE home_team IN ('SSG', 'SL');

UPDATE game
SET away_team = CASE away_team
    WHEN 'SSG' THEN 'SK'
    WHEN 'SL' THEN 'SK'
    ELSE away_team
END
WHERE away_team IN ('SSG', 'SL');

UPDATE game
SET winning_team = CASE winning_team
    WHEN 'SSG' THEN 'SK'
    WHEN 'SL' THEN 'SK'
    ELSE winning_team
END
WHERE winning_team IN ('SSG', 'SL');

UPDATE game_batting_stats
SET team_code = CASE team_code
    WHEN 'SSG' THEN 'SK'
    WHEN 'SL' THEN 'SK'
    ELSE team_code
END
WHERE team_code IN ('SSG', 'SL');

UPDATE game_pitching_stats
SET team_code = CASE team_code
    WHEN 'SSG' THEN 'SK'
    WHEN 'SL' THEN 'SK'
    ELSE team_code
END
WHERE team_code IN ('SSG', 'SL');

UPDATE game_lineups
SET team_code = CASE team_code
    WHEN 'SSG' THEN 'SK'
    WHEN 'SL' THEN 'SK'
    ELSE team_code
END
WHERE team_code IN ('SSG', 'SL');

UPDATE game_inning_scores
SET team_code = CASE team_code
    WHEN 'SSG' THEN 'SK'
    WHEN 'SL' THEN 'SK'
    ELSE team_code
END
WHERE team_code IN ('SSG', 'SL');

UPDATE player_movements
SET team_code = CASE team_code
    WHEN 'SSG' THEN 'SK'
    WHEN 'SL' THEN 'SK'
    ELSE team_code
END
WHERE team_code IN ('SSG', 'SL');

UPDATE team_daily_roster
SET team_code = CASE team_code
    WHEN 'SSG' THEN 'SK'
    WHEN 'SL' THEN 'SK'
    ELSE team_code
END
WHERE team_code IN ('SSG', 'SL');

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_class
        WHERE oid = to_regclass('team_season_batting_summary')
          AND relkind IN ('r', 'p')
    ) THEN
        UPDATE team_season_batting_summary
        SET team_id = CASE team_id
            WHEN 'SSG' THEN 'SK'
            WHEN 'SL' THEN 'SK'
            ELSE team_id
        END
        WHERE team_id IN ('SSG', 'SL');
    END IF;
END;
$$;

UPDATE player_season_batting
SET team_code = CASE team_code
    WHEN 'SSG' THEN 'SK'
    WHEN 'SL' THEN 'SK'
    ELSE team_code
END
WHERE team_code IN ('SSG', 'SL');

UPDATE player_season_pitching
SET team_code = CASE team_code
    WHEN 'SSG' THEN 'SK'
    WHEN 'SL' THEN 'SK'
    ELSE team_code
END
WHERE team_code IN ('SSG', 'SL');

UPDATE cheer_post
SET team_id = CASE team_id
    WHEN 'SSG' THEN 'SK'
    WHEN 'SL' THEN 'SK'
    ELSE team_id
END
WHERE team_id IN ('SSG', 'SL');

UPDATE team_profiles
SET team_id = CASE team_id
    WHEN 'SSG' THEN 'SK'
    WHEN 'SL' THEN 'SK'
    ELSE team_id
END
WHERE team_id IN ('SSG', 'SL');

UPDATE cheer_battle_votes
SET team_id = CASE team_id
    WHEN 'SSG' THEN 'SK'
    WHEN 'SL' THEN 'SK'
    ELSE team_id
END
WHERE team_id IN ('SSG', 'SL');

UPDATE users
SET favorite_team = CASE favorite_team
    WHEN 'SSG' THEN 'SK'
    WHEN 'SL' THEN 'SK'
    ELSE favorite_team
END
WHERE favorite_team IN ('SSG', 'SL');

CREATE OR REPLACE FUNCTION normalize_team_code(input_code text)
RETURNS text AS $$
BEGIN
    IF input_code IS NULL THEN
        RETURN NULL;
    END IF;

    CASE UPPER(BTRIM(input_code))
        WHEN 'KIA' THEN RETURN 'HT';
        WHEN 'KI' THEN RETURN 'WO';
        WHEN 'NX' THEN RETURN 'WO';
        WHEN 'DO' THEN RETURN 'OB';
        WHEN 'BE' THEN RETURN 'HH';
        WHEN 'SSG' THEN RETURN 'SK';
        WHEN 'SL' THEN RETURN 'SK';
        WHEN 'MBC' THEN RETURN 'LG';
        WHEN 'LOT' THEN RETURN 'LT';
        ELSE RETURN UPPER(BTRIM(input_code));
    END CASE;
END;
$$ LANGUAGE plpgsql;
