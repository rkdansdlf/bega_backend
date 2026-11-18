-- 계정 삭제 최초 요청일: 취소·재요청과 무관하게 한 번만 기록한다. 신고 증거 미디어 보관 기준일.
-- users.deletion_requested_at 은 TIMESTAMPTZ 다. 앱은 서버 시간대(Asia/Seoul)의 벽시계 시각(TIMESTAMP)으로 읽고 쓰므로
-- 백필도 같은 시간대의 벽시계 시각으로 변환한다. 세션 시간대에 기대지 않도록 명시한다.
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS first_deletion_requested_at TIMESTAMP;

-- 현재 삭제 주기 시작일. 확정 익명화·관리자 삭제에서도 지우지 않는다. 신고 증거의 기준일 계산에 쓴다.
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS deletion_cycle_started_at TIMESTAMP;

-- 기존 삭제 요청 건은 현재 요청 시각을 최초 요청일과 주기 시작일로 본다.
-- 이미 취소되어 값이 없는 건은 최초 요청일이 없으므로 검토 대상으로 남는다.
UPDATE users
   SET first_deletion_requested_at = deletion_requested_at AT TIME ZONE 'Asia/Seoul'
 WHERE first_deletion_requested_at IS NULL
   AND deletion_requested_at IS NOT NULL;

UPDATE users
   SET deletion_cycle_started_at = deletion_requested_at AT TIME ZONE 'Asia/Seoul'
 WHERE deletion_cycle_started_at IS NULL
   AND deletion_requested_at IS NOT NULL;

CREATE TABLE IF NOT EXISTS report_evidence_media_retention (
    id BIGSERIAL PRIMARY KEY,
    post_image_id BIGINT NOT NULL,
    post_id BIGINT NOT NULL,
    owner_user_id BIGINT NOT NULL,
    storage_path VARCHAR(512) NOT NULL,
    base_requested_at TIMESTAMP,
    cycle_started_at TIMESTAMP,
    retain_until TIMESTAMP,
    hard_limit_at TIMESTAMP,
    extension_until TIMESTAMP,
    extension_case_ref VARCHAR(64),
    extension_approved_by VARCHAR(64),
    legal_basis VARCHAR(512),
    legal_review_due_at TIMESTAMP,
    legal_hold_approved_by VARCHAR(64),
    urgent_review_started_at TIMESTAMP,
    urgent_review_due_at TIMESTAMP,
    status VARCHAR(32) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP,
    last_error VARCHAR(512),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    deleted_at TIMESTAMP,
    resolved_at TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);

-- 같은 저장소 객체에 살아 있는 행은 하나만 둔다. 복구되어 끝난 행(RESOLVED_RESTORED)은 감사용으로 남고, 같은 객체의
-- 새 등록을 막지 않는다.
CREATE UNIQUE INDEX IF NOT EXISTS uq_report_evidence_media_live_path
    ON report_evidence_media_retention (storage_path)
    WHERE status <> 'RESOLVED_RESTORED';

CREATE INDEX IF NOT EXISTS idx_report_evidence_media_status
    ON report_evidence_media_retention (status, next_attempt_at);
