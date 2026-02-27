-- V81: Ensure parties.teamid exists for Party.teamId mapping.
-- PostgreSQL equivalent of Oracle V81.

ALTER TABLE IF EXISTS parties
    ADD COLUMN IF NOT EXISTS teamid VARCHAR(20);

DO $$
BEGIN
    IF to_regclass('public.parties') IS NULL THEN
        RETURN;
    END IF;

    -- Backfill from team_id, then hometeam/home_team, then awayteam/away_team
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'parties' AND column_name = 'team_id'
    ) THEN
        EXECUTE 'UPDATE parties SET teamid = team_id WHERE teamid IS NULL AND team_id IS NOT NULL';
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'parties' AND column_name = 'hometeam'
    ) THEN
        EXECUTE 'UPDATE parties SET teamid = hometeam WHERE teamid IS NULL AND hometeam IS NOT NULL';
    ELSIF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'parties' AND column_name = 'home_team'
    ) THEN
        EXECUTE 'UPDATE parties SET teamid = home_team WHERE teamid IS NULL AND home_team IS NOT NULL';
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'parties' AND column_name = 'awayteam'
    ) THEN
        EXECUTE 'UPDATE parties SET teamid = awayteam WHERE teamid IS NULL AND awayteam IS NOT NULL';
    ELSIF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'parties' AND column_name = 'away_team'
    ) THEN
        EXECUTE 'UPDATE parties SET teamid = away_team WHERE teamid IS NULL AND away_team IS NOT NULL';
    END IF;

    EXECUTE 'UPDATE parties SET teamid = ''UNKNOWN'' WHERE teamid IS NULL';
END;
$$;

ALTER TABLE IF EXISTS parties
    ALTER COLUMN teamid SET DEFAULT 'UNKNOWN';

ALTER TABLE IF EXISTS parties
    ALTER COLUMN teamid SET NOT NULL;
