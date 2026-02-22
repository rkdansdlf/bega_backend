-- Normalize current team codes and deactivate legacy/special teams

-- Align franchise current codes with normalized team codes
UPDATE /*+ NO_PARALLEL */ team_franchises
SET current_code = 'HT'
WHERE id = 5;

-- Ensure current team names/short names reflect active franchises
UPDATE /*+ NO_PARALLEL */ teams SET team_name = '삼성 라이온즈', team_short_name = '삼성' WHERE team_id = 'SS';
UPDATE /*+ NO_PARALLEL */ teams SET team_name = '롯데 자이언츠', team_short_name = '롯데' WHERE team_id = 'LT';
UPDATE /*+ NO_PARALLEL */ teams SET team_name = 'LG 트윈스', team_short_name = 'LG' WHERE team_id = 'LG';
UPDATE /*+ NO_PARALLEL */ teams SET team_name = '두산 베어스', team_short_name = '두산' WHERE team_id = 'OB';
UPDATE /*+ NO_PARALLEL */ teams SET team_name = 'KIA 타이거즈', team_short_name = 'KIA' WHERE team_id = 'HT';
UPDATE /*+ NO_PARALLEL */ teams SET team_name = '키움 히어로즈', team_short_name = '키움' WHERE team_id = 'WO';
UPDATE /*+ NO_PARALLEL */ teams SET team_name = '한화 이글스', team_short_name = '한화' WHERE team_id = 'HH';
UPDATE /*+ NO_PARALLEL */ teams SET team_name = 'SSG 랜더스', team_short_name = 'SSG' WHERE team_id = 'SSG';
UPDATE /*+ NO_PARALLEL */ teams SET team_name = 'NC 다이노스', team_short_name = 'NC' WHERE team_id = 'NC';
UPDATE /*+ NO_PARALLEL */ teams SET team_name = 'KT 위즈', team_short_name = 'KT' WHERE team_id = 'KT';

-- Activate only normalized current teams; deactivate legacy/special teams
UPDATE /*+ NO_PARALLEL */ teams
SET is_active = CASE
    WHEN team_id IN ('SS', 'LT', 'LG', 'OB', 'HT', 'WO', 'HH', 'SSG', 'NC', 'KT') THEN true
    ELSE false
END;
