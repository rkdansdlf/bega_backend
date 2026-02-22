-- V32: 사용자 계정 상태 관리 필드 추가
-- [Security Fix] 계정 비활성화/잠금 기능 지원

-- enabled: 계정 활성화 여부 (기본값 1=true)
ALTER TABLE users ADD enabled NUMBER(1) DEFAULT 1 NOT NULL;

-- locked: 계정 잠금 여부 (기본값 0=false)
ALTER TABLE users ADD locked NUMBER(1) DEFAULT 0 NOT NULL;

-- lock_expires_at: 잠금 해제 예정 시간 (null이면 영구 잠금 또는 잠금 아님)
ALTER TABLE users ADD lock_expires_at TIMESTAMP;

-- 코멘트 추가
COMMENT ON COLUMN users.enabled IS '계정 활성화 여부 (0=비활성화, 1=활성화)';
COMMENT ON COLUMN users.locked IS '계정 잠금 여부 (0=정상, 1=잠금)';
COMMENT ON COLUMN users.lock_expires_at IS '계정 잠금 해제 예정 시간';
