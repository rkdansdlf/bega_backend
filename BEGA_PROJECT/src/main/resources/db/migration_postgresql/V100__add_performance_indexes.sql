-- V100: 성능 개선을 위한 복합 인덱스 추가
-- 주요 쿼리 경로: Navbar 폴링, JWT 필터, 스케줄러, 치어 피드, 예측 투표

-- 1. 알림 미읽음 카운트 (Navbar 30초 폴링 대상: WHERE user_id = ? AND is_read = false)
CREATE INDEX IF NOT EXISTS idx_notifications_user_unread
  ON notifications(user_id, is_read)
  WHERE is_read = false;

-- 2. 파티 상태/날짜 조회 (스케줄러 자동만료 + 목록 필터링)
CREATE INDEX IF NOT EXISTS idx_parties_status_game_date
  ON parties(status, game_date);

-- 3. 유저 상태 조회 (JWT 필터: 매 API 요청마다 enabled/locked 확인)
CREATE INDEX IF NOT EXISTS idx_users_auth_status
  ON users(enabled, locked);

-- 4. 치어 게시글 작성자 피드 (author_id 기준 최신순 정렬)
CREATE INDEX IF NOT EXISTS idx_cheer_posts_author_created
  ON cheer_posts(author_id, created_at DESC);

-- 5. 예측 투표 중복 체크 (game_id + user_id 복합 조회)
CREATE INDEX IF NOT EXISTS idx_predictions_game_user
  ON predictions(game_id, user_id);
