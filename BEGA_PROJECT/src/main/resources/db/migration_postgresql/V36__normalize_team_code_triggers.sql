-- Normalize legacy team codes on write and backfill remaining tables

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

-- Backfill remaining tables
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
END;

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
END;

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
END;

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
END;

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
END;

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
END;

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
END;

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
END;

-- Normalize function
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
        WHEN 'SK' THEN RETURN 'SSG';
        WHEN 'SL' THEN RETURN 'SSG';
        WHEN 'MBC' THEN RETURN 'LG';
        WHEN 'LOT' THEN RETURN 'LT';
        ELSE RETURN UPPER(BTRIM(input_code));
    END CASE;
END;
$$ LANGUAGE plpgsql;

-- Trigger functions
CREATE OR REPLACE FUNCTION normalize_game_team_codes()
RETURNS trigger AS $$
BEGIN
    NEW.home_team = normalize_team_code(NEW.home_team);
    NEW.away_team = normalize_team_code(NEW.away_team);
    NEW.winning_team = normalize_team_code(NEW.winning_team);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION normalize_team_code_column()
RETURNS trigger AS $$
BEGIN
    NEW.team_code = normalize_team_code(NEW.team_code);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION normalize_team_id_column()
RETURNS trigger AS $$
BEGIN
    NEW.team_id = normalize_team_code(NEW.team_id);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION normalize_favorite_team_column()
RETURNS trigger AS $$
BEGIN
    NEW.favorite_team = normalize_team_code(NEW.favorite_team);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Triggers
DROP TRIGGER IF EXISTS trg_normalize_game_team_codes ON game;
CREATE TRIGGER trg_normalize_game_team_codes
BEFORE INSERT OR UPDATE ON game
FOR EACH ROW
EXECUTE FUNCTION normalize_game_team_codes();

DROP TRIGGER IF EXISTS trg_normalize_game_batting_stats_team_code ON game_batting_stats;
CREATE TRIGGER trg_normalize_game_batting_stats_team_code
BEFORE INSERT OR UPDATE ON game_batting_stats
FOR EACH ROW
EXECUTE FUNCTION normalize_team_code_column();

DROP TRIGGER IF EXISTS trg_normalize_game_pitching_stats_team_code ON game_pitching_stats;
CREATE TRIGGER trg_normalize_game_pitching_stats_team_code
BEFORE INSERT OR UPDATE ON game_pitching_stats
FOR EACH ROW
EXECUTE FUNCTION normalize_team_code_column();

DROP TRIGGER IF EXISTS trg_normalize_game_lineups_team_code ON game_lineups;
CREATE TRIGGER trg_normalize_game_lineups_team_code
BEFORE INSERT OR UPDATE ON game_lineups
FOR EACH ROW
EXECUTE FUNCTION normalize_team_code_column();

DROP TRIGGER IF EXISTS trg_normalize_game_inning_scores_team_code ON game_inning_scores;
CREATE TRIGGER trg_normalize_game_inning_scores_team_code
BEFORE INSERT OR UPDATE ON game_inning_scores
FOR EACH ROW
EXECUTE FUNCTION normalize_team_code_column();

DROP TRIGGER IF EXISTS trg_normalize_player_movements_team_code ON player_movements;
CREATE TRIGGER trg_normalize_player_movements_team_code
BEFORE INSERT OR UPDATE ON player_movements
FOR EACH ROW
EXECUTE FUNCTION normalize_team_code_column();

DROP TRIGGER IF EXISTS trg_normalize_team_daily_roster_team_code ON team_daily_roster;
CREATE TRIGGER trg_normalize_team_daily_roster_team_code
BEFORE INSERT OR UPDATE ON team_daily_roster
FOR EACH ROW
EXECUTE FUNCTION normalize_team_code_column();

DROP TRIGGER IF EXISTS trg_normalize_player_season_batting_team_code ON player_season_batting;
CREATE TRIGGER trg_normalize_player_season_batting_team_code
BEFORE INSERT OR UPDATE ON player_season_batting
FOR EACH ROW
EXECUTE FUNCTION normalize_team_code_column();

DROP TRIGGER IF EXISTS trg_normalize_player_season_pitching_team_code ON player_season_pitching;
CREATE TRIGGER trg_normalize_player_season_pitching_team_code
BEFORE INSERT OR UPDATE ON player_season_pitching
FOR EACH ROW
EXECUTE FUNCTION normalize_team_code_column();

DROP TRIGGER IF EXISTS trg_normalize_team_season_batting_summary_team_id ON team_season_batting_summary;
CREATE TRIGGER trg_normalize_team_season_batting_summary_team_id
BEFORE INSERT OR UPDATE ON team_season_batting_summary
FOR EACH ROW
EXECUTE FUNCTION normalize_team_id_column();

DROP TRIGGER IF EXISTS trg_normalize_team_profiles_team_id ON team_profiles;
CREATE TRIGGER trg_normalize_team_profiles_team_id
BEFORE INSERT OR UPDATE ON team_profiles
FOR EACH ROW
EXECUTE FUNCTION normalize_team_id_column();

DROP TRIGGER IF EXISTS trg_normalize_cheer_post_team_id ON cheer_post;
CREATE TRIGGER trg_normalize_cheer_post_team_id
BEFORE INSERT OR UPDATE ON cheer_post
FOR EACH ROW
EXECUTE FUNCTION normalize_team_id_column();

DROP TRIGGER IF EXISTS trg_normalize_cheer_battle_votes_team_id ON cheer_battle_votes;
CREATE TRIGGER trg_normalize_cheer_battle_votes_team_id
BEFORE INSERT OR UPDATE ON cheer_battle_votes
FOR EACH ROW
EXECUTE FUNCTION normalize_team_id_column();

DROP TRIGGER IF EXISTS trg_normalize_users_favorite_team ON users;
CREATE TRIGGER trg_normalize_users_favorite_team
BEFORE INSERT OR UPDATE ON users
FOR EACH ROW
EXECUTE FUNCTION normalize_favorite_team_column();
