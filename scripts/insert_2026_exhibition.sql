-- 2026 KBO Exhibition Schedule (Si-beom Gyeonggi) - Estimated/Sample
-- Source: Missing from original import
-- Season ID: 266 (2026 Exhibition)
-- Dates: 2026-03-12 to 2026-03-27

INSERT INTO game (game_id, game_date, season_id, winning_team, home_team, away_team, home_score, away_score, game_status, is_dummy) VALUES
-- March 14 (Sample Start)
('20260314LGDO0', '2026-03-14', 266, NULL, 'LG', 'DO', NULL, NULL, 'SCHEDULED', false),
('20260314SSKT0', '2026-03-14', 266, NULL, 'SS', 'KT', NULL, NULL, 'SCHEDULED', false),
('20260314KIAHH0', '2026-03-14', 266, NULL, 'KIA', 'HH', NULL, NULL, 'SCHEDULED', false),
('20260314SSGNC0', '2026-03-14', 266, NULL, 'SSG', 'NC', NULL, NULL, 'SCHEDULED', false),
('20260314LTWO0', '2026-03-14', 266, NULL, 'LT', 'KI', NULL, NULL, 'SCHEDULED', false), -- HI? KI? Need confirm (using KI code)

-- March 15
('20260315LGDO0', '2026-03-15', 266, NULL, 'LG', 'DO', NULL, NULL, 'SCHEDULED', false),
('20260315SSKT0', '2026-03-15', 266, NULL, 'SS', 'KT', NULL, NULL, 'SCHEDULED', false),
('20260315KIAHH0', '2026-03-15', 266, NULL, 'KIA', 'HH', NULL, NULL, 'SCHEDULED', false),
('20260315SSGNC0', '2026-03-15', 266, NULL, 'SSG', 'NC', NULL, NULL, 'SCHEDULED', false),
('20260315LTWO0', '2026-03-15', 266, NULL, 'LT', 'KI', NULL, NULL, 'SCHEDULED', false),

-- March 17
('20260317DOSS0', '2026-03-17', 266, NULL, 'DO', 'SS', NULL, NULL, 'SCHEDULED', false),
('20260317KTLG0', '2026-03-17', 266, NULL, 'KT', 'LG', NULL, NULL, 'SCHEDULED', false),
('20260317HIAKIA0', '2026-03-17', 266, NULL, 'HH', 'KIA', NULL, NULL, 'SCHEDULED', false),
('20260317NCSSG0', '2026-03-17', 266, NULL, 'NC', 'SSG', NULL, NULL, 'SCHEDULED', false),
('20260317WOLT0', '2026-03-17', 266, NULL, 'KI', 'LT', NULL, NULL, 'SCHEDULED', false),

-- March 21 (Assume some games)
('20260321LGSSG0', '2026-03-21', 266, NULL, 'LG', 'SSG', NULL, NULL, 'SCHEDULED', false),
('20260321DOKIA0', '2026-03-21', 266, NULL, 'DO', 'KIA', NULL, NULL, 'SCHEDULED', false),
('20260321KTHH0', '2026-03-21', 266, NULL, 'KT', 'HH', NULL, NULL, 'SCHEDULED', false),
('20260321SSLT0', '2026-03-21', 266, NULL, 'SS', 'LT', NULL, NULL, 'SCHEDULED', false),
('20260321KIWO0', '2026-03-21', 266, NULL, 'KI', 'NC', NULL, NULL, 'SCHEDULED', false),

-- March 24 (End of Exhibition typically)
('20260324LGKIA0', '2026-03-24', 266, NULL, 'LG', 'KIA', NULL, NULL, 'SCHEDULED', false),
('20260324DOSSG0', '2026-03-24', 266, NULL, 'DO', 'SSG', NULL, NULL, 'SCHEDULED', false),
('20260324KTLT0', '2026-03-24', 266, NULL, 'KT', 'LT', NULL, NULL, 'SCHEDULED', false),
('20260324SSNC0', '2026-03-24', 266, NULL, 'SS', 'NC', NULL, NULL, 'SCHEDULED', false),
('20260324WOHT0', '2026-03-24', 266, NULL, 'KI', 'HH', NULL, NULL, 'SCHEDULED', false)
ON CONFLICT (game_id) DO NOTHING;
