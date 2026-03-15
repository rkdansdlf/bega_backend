-- Standardize SSG legacy code to SK canonical code.
-- Existing triggers call normalize_team_code(), so redefining this function
-- flips write-time normalization without recreating triggers.

CREATE OR REPLACE FUNCTION __bega_exec_if_table_exists(target_table text, statement text)
RETURNS void AS $$
BEGIN
    IF to_regclass(target_table) IS NOT NULL THEN
        EXECUTE statement;
    END IF;
END;
$$ LANGUAGE plpgsql;

-- Backfill previously normalized rows (SSG/SL -> SK)
SELECT __bega_exec_if_table_exists('game', $sql$
UPDATE game
SET home_team = CASE home_team
    WHEN 'SSG' THEN 'SK'
    WHEN 'SL' THEN 'SK'
    ELSE home_team
END
WHERE home_team IN ('SSG', 'SL')
$sql$);

SELECT __bega_exec_if_table_exists('game', $sql$
UPDATE game
SET away_team = CASE away_team
    WHEN 'SSG' THEN 'SK'
    WHEN 'SL' THEN 'SK'
    ELSE away_team
END
WHERE away_team IN ('SSG', 'SL')
$sql$);

SELECT __bega_exec_if_table_exists('game', $sql$
UPDATE game
SET winning_team = CASE winning_team
    WHEN 'SSG' THEN 'SK'
    WHEN 'SL' THEN 'SK'
    ELSE winning_team
END
WHERE winning_team IN ('SSG', 'SL')
$sql$);

SELECT __bega_exec_if_table_exists('game_batting_stats', $sql$
UPDATE game_batting_stats
SET team_code = CASE team_code
    WHEN 'SSG' THEN 'SK'
    WHEN 'SL' THEN 'SK'
    ELSE team_code
END
WHERE team_code IN ('SSG', 'SL')
$sql$);

SELECT __bega_exec_if_table_exists('game_pitching_stats', $sql$
UPDATE game_pitching_stats
SET team_code = CASE team_code
    WHEN 'SSG' THEN 'SK'
    WHEN 'SL' THEN 'SK'
    ELSE team_code
END
WHERE team_code IN ('SSG', 'SL')
$sql$);

SELECT __bega_exec_if_table_exists('game_lineups', $sql$
UPDATE game_lineups
SET team_code = CASE team_code
    WHEN 'SSG' THEN 'SK'
    WHEN 'SL' THEN 'SK'
    ELSE team_code
END
WHERE team_code IN ('SSG', 'SL')
$sql$);

SELECT __bega_exec_if_table_exists('game_inning_scores', $sql$
UPDATE game_inning_scores
SET team_code = CASE team_code
    WHEN 'SSG' THEN 'SK'
    WHEN 'SL' THEN 'SK'
    ELSE team_code
END
WHERE team_code IN ('SSG', 'SL')
$sql$);

SELECT __bega_exec_if_table_exists('player_movements', $sql$
UPDATE player_movements
SET team_code = CASE team_code
    WHEN 'SSG' THEN 'SK'
    WHEN 'SL' THEN 'SK'
    ELSE team_code
END
WHERE team_code IN ('SSG', 'SL')
$sql$);

SELECT __bega_exec_if_table_exists('team_daily_roster', $sql$
UPDATE team_daily_roster
SET team_code = CASE team_code
    WHEN 'SSG' THEN 'SK'
    WHEN 'SL' THEN 'SK'
    ELSE team_code
END
WHERE team_code IN ('SSG', 'SL')
$sql$);

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

SELECT __bega_exec_if_table_exists('player_season_batting', $sql$
UPDATE player_season_batting
SET team_code = CASE team_code
    WHEN 'SSG' THEN 'SK'
    WHEN 'SL' THEN 'SK'
    ELSE team_code
END
WHERE team_code IN ('SSG', 'SL')
$sql$);

SELECT __bega_exec_if_table_exists('player_season_pitching', $sql$
UPDATE player_season_pitching
SET team_code = CASE team_code
    WHEN 'SSG' THEN 'SK'
    WHEN 'SL' THEN 'SK'
    ELSE team_code
END
WHERE team_code IN ('SSG', 'SL')
$sql$);

SELECT __bega_exec_if_table_exists('cheer_post', $sql$
UPDATE cheer_post
SET team_id = CASE team_id
    WHEN 'SSG' THEN 'SK'
    WHEN 'SL' THEN 'SK'
    ELSE team_id
END
WHERE team_id IN ('SSG', 'SL')
$sql$);

SELECT __bega_exec_if_table_exists('team_profiles', $sql$
UPDATE team_profiles
SET team_id = CASE team_id
    WHEN 'SSG' THEN 'SK'
    WHEN 'SL' THEN 'SK'
    ELSE team_id
END
WHERE team_id IN ('SSG', 'SL')
$sql$);

SELECT __bega_exec_if_table_exists('cheer_battle_votes', $sql$
UPDATE cheer_battle_votes
SET team_id = CASE team_id
    WHEN 'SSG' THEN 'SK'
    WHEN 'SL' THEN 'SK'
    ELSE team_id
END
WHERE team_id IN ('SSG', 'SL')
$sql$);

SELECT __bega_exec_if_table_exists('users', $sql$
UPDATE users
SET favorite_team = CASE favorite_team
    WHEN 'SSG' THEN 'SK'
    WHEN 'SL' THEN 'SK'
    ELSE favorite_team
END
WHERE favorite_team IN ('SSG', 'SL')
$sql$);

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

DROP FUNCTION IF EXISTS __bega_exec_if_table_exists(text, text);
