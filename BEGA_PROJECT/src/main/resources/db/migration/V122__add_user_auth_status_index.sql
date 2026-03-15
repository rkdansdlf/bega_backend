-- V122: 유저 인증 상태 조회 성능 인덱스 추가
-- JWT 필터에서 매 요청마다 enabled/locked 상태를 조회하는 풀스캔 방지

CREATE INDEX IF NOT EXISTS idx_users_auth_status
    ON users (enabled, locked);
