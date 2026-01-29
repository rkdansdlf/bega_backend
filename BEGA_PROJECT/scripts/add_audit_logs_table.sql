-- ============================================
-- 어드민 감사 로그 테이블 생성 마이그레이션
-- 실행(마이그레이션용 Supabase): psql $SUPABASE_DB_URL -f add_audit_logs_table.sql
-- ============================================

-- admin_audit_logs 테이블 생성
CREATE TABLE IF NOT EXISTS security.admin_audit_logs (
    id BIGSERIAL PRIMARY KEY,
    admin_id BIGINT NOT NULL,
    target_user_id BIGINT NOT NULL,
    action VARCHAR(50) NOT NULL,
    old_value VARCHAR(100),
    new_value VARCHAR(100),
    description VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- 외래키 제약조건 (참조 무결성)
    CONSTRAINT fk_audit_admin FOREIGN KEY (admin_id)
        REFERENCES security.users(id) ON DELETE SET NULL,
    CONSTRAINT fk_audit_target FOREIGN KEY (target_user_id)
        REFERENCES security.users(id) ON DELETE SET NULL
);

-- 성능 최적화 인덱스
CREATE INDEX IF NOT EXISTS idx_audit_logs_admin_id
    ON security.admin_audit_logs(admin_id);
CREATE INDEX IF NOT EXISTS idx_audit_logs_target_user_id
    ON security.admin_audit_logs(target_user_id);
CREATE INDEX IF NOT EXISTS idx_audit_logs_created_at
    ON security.admin_audit_logs(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_logs_action
    ON security.admin_audit_logs(action);

-- 테이블 코멘트
COMMENT ON TABLE security.admin_audit_logs IS '관리자 작업 감사 로그';
COMMENT ON COLUMN security.admin_audit_logs.admin_id IS '작업을 수행한 관리자 ID';
COMMENT ON COLUMN security.admin_audit_logs.target_user_id IS '작업 대상 사용자 ID';
COMMENT ON COLUMN security.admin_audit_logs.action IS '작업 유형 (PROMOTE_TO_ADMIN, DEMOTE_TO_USER, DELETE_USER, DELETE_POST, DELETE_MATE)';
COMMENT ON COLUMN security.admin_audit_logs.old_value IS '변경 전 값';
COMMENT ON COLUMN security.admin_audit_logs.new_value IS '변경 후 값';
COMMENT ON COLUMN security.admin_audit_logs.description IS '추가 설명 또는 사유';

-- ============================================
-- SUPER_ADMIN 설정 (필요 시 주석 해제 후 실행)
-- ============================================
-- 기존 관리자 중 하나를 SUPER_ADMIN으로 승격
-- UPDATE security.users SET role = 'ROLE_SUPER_ADMIN' WHERE id = <관리자_ID>;

-- 특정 이메일의 사용자를 SUPER_ADMIN으로 설정
-- UPDATE security.users SET role = 'ROLE_SUPER_ADMIN' WHERE email = 'admin@example.com';

-- 확인 쿼리
-- SELECT id, email, name, role FROM security.users WHERE role LIKE '%ADMIN%';
