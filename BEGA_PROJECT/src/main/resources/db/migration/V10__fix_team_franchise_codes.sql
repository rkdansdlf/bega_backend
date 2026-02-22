-- Fix team_franchise_codes
-- Ensure current_code is unique and correct for all 10 franchises
-- Particularly fixing SSG (id=8) which was incorrectly set to 'SS' causing duplication with Samsung

UPDATE team_franchises SET current_code = 'SS' WHERE id = 1;
UPDATE team_franchises SET current_code = 'LT' WHERE id = 2;
UPDATE team_franchises SET current_code = 'LG' WHERE id = 3;
UPDATE team_franchises SET current_code = 'OB' WHERE id = 4;
UPDATE team_franchises SET current_code = 'KIA' WHERE id = 5;
UPDATE team_franchises SET current_code = 'WO' WHERE id = 6;
UPDATE team_franchises SET current_code = 'HH' WHERE id = 7;
UPDATE team_franchises SET current_code = 'SSG' WHERE id = 8;
UPDATE team_franchises SET current_code = 'NC' WHERE id = 9;
UPDATE team_franchises SET current_code = 'KT' WHERE id = 10;
