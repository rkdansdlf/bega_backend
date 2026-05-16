-- places: stadium_id 단독 인덱스 추가
-- 기존 복합 인덱스 (stadium_id, category)는 stadium_id 단독 조회에서 활용되지 않을 수 있으므로 추가
CREATE INDEX IF NOT EXISTS idx_places_stadium_id ON places (stadium_id);

-- user_stadium_favorites: stadium_id 인덱스 추가 (stadium 기준 집계 쿼리 대비)
CREATE INDEX IF NOT EXISTS idx_user_stadium_favorites_stadium_id ON user_stadium_favorites (stadium_id);
