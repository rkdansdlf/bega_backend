-- V198 이후 변경분: 삭제 점유, 삭제 사유, 보존 이력 테이블.
-- V198 은 아직 어느 DB 에도 적용되지 않았으므로 V198 을 고치지 않고 이 파일에서 덧붙인다.

-- 객체 삭제 호출 전에 찍는 점유 시각. DB 행 잠금을 외부 호출 동안 잡지 않으려고 쓴다.
ALTER TABLE report_evidence_media_retention
    ADD COLUMN IF NOT EXISTS delete_claimed_at TIMESTAMP;

-- 삭제 대기로 넘어간 이유 (CASE_CLOSED, HOLD_LAPSED, URGENT_GRACE_EXPIRED).
ALTER TABLE report_evidence_media_retention
    ADD COLUMN IF NOT EXISTS delete_reason VARCHAR(32);

-- 보존 기록의 변경 이력. 추가만 가능하며, 아래 트리거가 수정과 삭제를 거부한다.
CREATE TABLE IF NOT EXISTS report_evidence_media_retention_event (
    id BIGSERIAL PRIMARY KEY,
    retention_id BIGINT NOT NULL,
    event_type VARCHAR(40) NOT NULL,
    reason VARCHAR(32),
    actor VARCHAR(64) NOT NULL,
    case_ref VARCHAR(64),
    previous_value VARCHAR(100),
    new_value VARCHAR(100),
    basis_text VARCHAR(512),
    previous_basis VARCHAR(512),
    previous_actor VARCHAR(64),
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_report_evidence_event_retention
    ON report_evidence_media_retention_event (retention_id, id);

CREATE OR REPLACE FUNCTION reject_report_evidence_event_change() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'report_evidence_media_retention_event 은 추가만 가능합니다 (수정과 삭제 거부)';
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_report_evidence_event_append_only ON report_evidence_media_retention_event;
CREATE TRIGGER trg_report_evidence_event_append_only
    BEFORE UPDATE OR DELETE ON report_evidence_media_retention_event
    FOR EACH ROW EXECUTE FUNCTION reject_report_evidence_event_change();

-- 행은 게시글 이미지마다 하나다. 같은 저장소 객체를 여러 게시글이 가리켜도 각 게시글의 보존 기간이 따로 간다.
-- 운영자 삭제 승인의 사건 번호.
ALTER TABLE report_evidence_media_retention
    ADD COLUMN IF NOT EXISTS release_case_ref VARCHAR(64);

-- 객체 삭제는 다른 살아 있는 행이 보호하는 동안 미뤄진다(삭제 대기 행의 재시도). 그래서 경로 유일성은 두지 않는다.
DROP INDEX IF EXISTS uq_report_evidence_media_live_path;
CREATE UNIQUE INDEX IF NOT EXISTS uq_report_evidence_media_live_image
    ON report_evidence_media_retention (post_image_id)
    WHERE status <> 'RESOLVED_RESTORED';

CREATE INDEX IF NOT EXISTS idx_report_evidence_media_path
    ON report_evidence_media_retention (storage_path);
