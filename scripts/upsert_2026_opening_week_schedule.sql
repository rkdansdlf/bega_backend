-- 2026 opening week bootstrap schedule upsert
-- 목적: /api/kbo/schedule, /api/matches/range, /api/matches/bounds에서 2026 시즌 단절을 즉시 완화
-- 주의: 운영 원본 적재가 완료되면 이 파일 기반 데이터와 차이를 재검증해야 함

INSERT INTO game (
  game_id,
  game_date,
  season_id,
  stadium,
  home_team,
  away_team,
  game_status,
  is_dummy
)
VALUES
  ('20260322LTLG0', DATE '2026-03-22', NULL, '잠실', 'LG', 'LT', 'SCHEDULED', FALSE),
  ('20260322SSKT0', DATE '2026-03-22', NULL, '수원', 'KT', 'SS', 'SCHEDULED', FALSE),
  ('20260322NCSSG0', DATE '2026-03-22', NULL, '문학', 'SSG', 'NC', 'SCHEDULED', FALSE),
  ('20260322KIAKH0', DATE '2026-03-22', NULL, '고척', 'KH', 'KIA', 'SCHEDULED', FALSE),
  ('20260322HHDB0', DATE '2026-03-22', NULL, '대전', 'DB', 'HH', 'SCHEDULED', FALSE),

  ('20260323LTLG0', DATE '2026-03-23', NULL, '잠실', 'LG', 'LT', 'SCHEDULED', FALSE),
  ('20260323SSKT0', DATE '2026-03-23', NULL, '수원', 'KT', 'SS', 'SCHEDULED', FALSE),
  ('20260323NCSSG0', DATE '2026-03-23', NULL, '문학', 'SSG', 'NC', 'SCHEDULED', FALSE),
  ('20260323KIAKH0', DATE '2026-03-23', NULL, '고척', 'KH', 'KIA', 'SCHEDULED', FALSE),
  ('20260323HHDB0', DATE '2026-03-23', NULL, '대전', 'DB', 'HH', 'SCHEDULED', FALSE),

  ('20260328LTLG0', DATE '2026-03-28', NULL, '사직', 'LT', 'LG', 'SCHEDULED', FALSE),
  ('20260328SSKT0', DATE '2026-03-28', NULL, '대구', 'SS', 'KT', 'SCHEDULED', FALSE),
  ('20260328NCSSG0', DATE '2026-03-28', NULL, '창원', 'NC', 'SSG', 'SCHEDULED', FALSE),
  ('20260328KIAKH0', DATE '2026-03-28', NULL, '광주', 'KIA', 'KH', 'SCHEDULED', FALSE),
  ('20260328HHDB0', DATE '2026-03-28', NULL, '대전', 'HH', 'DB', 'SCHEDULED', FALSE)
ON CONFLICT (game_id) DO UPDATE
SET
  game_date = EXCLUDED.game_date,
  season_id = EXCLUDED.season_id,
  stadium = EXCLUDED.stadium,
  home_team = EXCLUDED.home_team,
  away_team = EXCLUDED.away_team,
  game_status = EXCLUDED.game_status,
  is_dummy = EXCLUDED.is_dummy;
