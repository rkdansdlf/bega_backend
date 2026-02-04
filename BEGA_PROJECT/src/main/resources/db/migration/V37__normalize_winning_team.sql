-- Normalize winning_team values to actual team codes (home/away -> team_id)

UPDATE /*+ NO_PARALLEL */ game
SET winning_team = CASE
    WHEN LOWER(TRIM(winning_team)) = 'home' THEN home_team
    WHEN LOWER(TRIM(winning_team)) = 'away' THEN away_team
    ELSE winning_team
END
WHERE winning_team IS NOT NULL
  AND LOWER(TRIM(winning_team)) IN ('home', 'away');
